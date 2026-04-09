#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import shutil
from datetime import datetime, timezone
from pathlib import Path

from analyst_storage import cache_root, export_and_scan_cache_root, helper_tmp_root, inputs_cache_root, work_root
from planner import PlannerError, build_plan, build_plan_error_payload
from runner import (
    build_analysis_error_result,
    collect_input_paths,
    ensure_run_root,
    is_reusable_step,
    load_step_reuse_index,
    load_step_result_file,
    make_run_id,
    persist_run_outputs,
    prune_invalid_step_reuse_entries,
    run_plan,
    step_reuse_index_path,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Plan or run a constrained version 1 analyst task on top of the launcher-backed dexclub-cli helpers.",
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    plan_parser = subparsers.add_parser("plan", help="Emit a structured execution plan.")
    plan_parser.add_argument("--task-type", required=True, help="Version 1 task type.")
    plan_parser.add_argument("--input-json", required=True, help="Task input JSON object.")
    plan_parser.add_argument("--artifact-root", help="Optional explicit artifact root for planned steps.")

    run_parser = subparsers.add_parser("run", help="Plan and immediately execute a version 1 task.")
    run_parser.add_argument("--task-type", required=True, help="Version 1 task type.")
    run_parser.add_argument("--input-json", required=True, help="Task input JSON object.")
    run_parser.add_argument("--artifact-root", help="Optional explicit artifact root.")

    cache_parser = subparsers.add_parser("cache", help="Inspect, prune, or clear analyst-managed caches.")
    cache_subparsers = cache_parser.add_subparsers(dest="cache_command", required=True)

    cache_inspect_parser = cache_subparsers.add_parser("inspect", help="Inspect current cache/index state.")
    cache_inspect_parser.add_argument("--format", choices=("text", "json"), default="json", help="Output format.")

    cache_prune_parser = cache_subparsers.add_parser("prune", help="Prune invalid cache/index entries.")
    cache_prune_parser.add_argument("--format", choices=("text", "json"), default="json", help="Output format.")

    cache_clear_parser = cache_subparsers.add_parser("clear", help="Clear selected cache/index scopes.")
    cache_clear_parser.add_argument(
        "--scope",
        action="append",
        choices=("inputs", "export-and-scan", "reusable-steps", "tmp", "all"),
        help="Cache scope to clear. Repeatable. Defaults to all managed scopes.",
    )
    cache_clear_parser.add_argument("--format", choices=("text", "json"), default="json", help="Output format.")
    return parser.parse_args()


def load_input_json(raw_value: str) -> dict[str, object]:
    try:
        payload = json.loads(raw_value)
    except json.JSONDecodeError as exc:
        raise PlannerError("input_error", f"`input-json` is not valid JSON: {exc.msg}") from exc
    if not isinstance(payload, dict):
        raise PlannerError("input_error", "`input-json` must decode to a JSON object.")
    return payload


def file_tree_stats(path: Path) -> dict[str, object]:
    if not path.exists():
        return {
            "exists": False,
            "dir_count": 0,
            "file_count": 0,
            "total_bytes": 0,
        }
    dir_count = 0
    file_count = 0
    total_bytes = 0
    for node in path.rglob("*"):
        if node.is_dir():
            dir_count += 1
        elif node.is_file():
            file_count += 1
            total_bytes += node.stat().st_size
    return {
        "exists": True,
        "dir_count": dir_count,
        "file_count": file_count,
        "total_bytes": total_bytes,
    }


def inspect_inputs_cache() -> dict[str, object]:
    root = inputs_cache_root()
    dex_root = root / "dex"
    apk_root = root / "apk"
    dex_entries = [path for path in dex_root.iterdir() if path.is_dir()] if dex_root.is_dir() else []
    apk_entries = [path for path in apk_root.iterdir() if path.is_dir()] if apk_root.is_dir() else []
    invalid_dex = [path for path in dex_entries if not ((path / "input.dex").is_file() and (path / "input-meta.json").is_file())]
    invalid_apk: list[Path] = []
    for path in apk_entries:
        if not (path / "input-meta.json").is_file():
            invalid_apk.append(path)
            continue
        extracted_dir = path / "extracted-dex"
        if extracted_dir.exists() and not any(extracted_dir.glob("*.dex")):
            invalid_apk.append(path)
    return {
        "path": str(root.resolve()),
        "stats": file_tree_stats(root),
        "dex_entry_count": len(dex_entries),
        "apk_entry_count": len(apk_entries),
        "invalid_entry_count": len(invalid_dex) + len(invalid_apk),
        "invalid_entries": [str(path.resolve()) for path in [*invalid_dex, *invalid_apk]],
    }


def inspect_export_and_scan_cache() -> dict[str, object]:
    root = export_and_scan_cache_root()
    entry_dirs: list[Path] = []
    if root.is_dir():
        for digest_dir in root.iterdir():
            if not digest_dir.is_dir():
                continue
            for entry_dir in digest_dir.iterdir():
                if entry_dir.is_dir():
                    entry_dirs.append(entry_dir)
    invalid_entries: list[Path] = []
    for entry_dir in entry_dirs:
        has_report = (entry_dir / "analysis-report.json").is_file()
        has_export = any(child.is_file() and child.suffix.lower() in {".smali", ".java"} for child in entry_dir.iterdir())
        if not (has_report and has_export):
            invalid_entries.append(entry_dir)
    return {
        "path": str(root.resolve()),
        "stats": file_tree_stats(root),
        "entry_count": len(entry_dirs),
        "invalid_entry_count": len(invalid_entries),
        "invalid_entries": [str(path.resolve()) for path in invalid_entries],
    }


def inspect_reusable_step_index() -> dict[str, object]:
    index_path = step_reuse_index_path()
    payload = load_step_reuse_index()
    entries = payload.get("entries", {})
    valid_count = 0
    invalid_count = 0
    step_kind_counts: dict[str, int] = {}
    release_tag_counts: dict[str, int] = {}
    invalid_entries: list[str] = []
    if isinstance(entries, dict):
        for reuse_key, entry in entries.items():
            if not isinstance(entry, dict):
                invalid_count += 1
                invalid_entries.append(str(reuse_key))
                continue
            step_kind = entry.get("step_kind")
            if isinstance(step_kind, str) and step_kind:
                step_kind_counts[step_kind] = step_kind_counts.get(step_kind, 0) + 1
            release_tag = entry.get("release_tag")
            release_key = str(release_tag) if isinstance(release_tag, str) and release_tag else "<none>"
            release_tag_counts[release_key] = release_tag_counts.get(release_key, 0) + 1
            step_result_path = entry.get("step_result_path")
            if not isinstance(step_result_path, str) or not step_result_path:
                invalid_count += 1
                invalid_entries.append(str(reuse_key))
                continue
            step_result = load_step_result_file(Path(step_result_path))
            if step_result is None or not is_reusable_step(step_result):
                invalid_count += 1
                invalid_entries.append(str(reuse_key))
                continue
            valid_count += 1
    return {
        "path": str(index_path.resolve()),
        "exists": index_path.is_file(),
        "entry_count": valid_count + invalid_count,
        "valid_entry_count": valid_count,
        "invalid_entry_count": invalid_count,
        "step_kind_counts": step_kind_counts,
        "release_tag_counts": release_tag_counts,
        "invalid_entries": invalid_entries,
    }


def inspect_tmp_root() -> dict[str, object]:
    root = helper_tmp_root()
    entries = [path for path in root.iterdir()] if root.is_dir() else []
    return {
        "path": str(root.resolve()),
        "stats": file_tree_stats(root),
        "entry_count": len(entries),
    }


def build_cache_inspect_payload() -> dict[str, object]:
    return {
        "kind": "cache_inspect",
        "work_root": str(work_root().resolve()),
        "cache_root": str(cache_root().resolve()),
        "inputs": inspect_inputs_cache(),
        "export_and_scan": inspect_export_and_scan_cache(),
        "reusable_steps": inspect_reusable_step_index(),
        "tmp": inspect_tmp_root(),
    }


def prune_inputs_cache() -> dict[str, object]:
    summary = inspect_inputs_cache()
    removed_paths: list[str] = []
    for raw_path in summary["invalid_entries"]:
        path = Path(raw_path)
        if path.exists():
            shutil.rmtree(path, ignore_errors=True)
            removed_paths.append(str(path.resolve()))
    return {
        "removed_entry_count": len(removed_paths),
        "removed_paths": removed_paths,
    }


def prune_export_and_scan_cache() -> dict[str, object]:
    summary = inspect_export_and_scan_cache()
    removed_paths: list[str] = []
    for raw_path in summary["invalid_entries"]:
        path = Path(raw_path)
        if path.exists():
            parent = path.parent
            shutil.rmtree(path, ignore_errors=True)
            removed_paths.append(str(path.resolve()))
            if parent.is_dir() and not any(parent.iterdir()):
                parent.rmdir()
    return {
        "removed_entry_count": len(removed_paths),
        "removed_paths": removed_paths,
    }


def build_cache_prune_payload() -> dict[str, object]:
    inputs_result = prune_inputs_cache()
    export_result = prune_export_and_scan_cache()
    reusable_removed_count = prune_invalid_step_reuse_entries()
    return {
        "kind": "cache_prune",
        "work_root": str(work_root().resolve()),
        "cache_root": str(cache_root().resolve()),
        "inputs": inputs_result,
        "export_and_scan": export_result,
        "reusable_steps": {
            "removed_entry_count": reusable_removed_count,
            "path": str(step_reuse_index_path().resolve()),
        },
    }


def normalized_clear_scopes(raw_scopes: list[str] | None) -> list[str]:
    if not raw_scopes or "all" in raw_scopes:
        return ["inputs", "export-and-scan", "reusable-steps", "tmp"]
    ordered: list[str] = []
    for scope in raw_scopes:
        if scope not in ordered:
            ordered.append(scope)
    return ordered


def clear_path(path: Path) -> bool:
    if path.is_file():
        path.unlink()
        return True
    if path.is_dir():
        shutil.rmtree(path, ignore_errors=True)
        return True
    return False


def build_cache_clear_payload(scopes: list[str]) -> dict[str, object]:
    normalized_scopes = normalized_clear_scopes(scopes)
    cleared: dict[str, dict[str, object]] = {}
    for scope in normalized_scopes:
        if scope == "inputs":
            path = inputs_cache_root()
        elif scope == "export-and-scan":
            path = export_and_scan_cache_root()
        elif scope == "reusable-steps":
            path = step_reuse_index_path()
        elif scope == "tmp":
            path = helper_tmp_root()
        else:
            continue
        cleared[scope] = {
            "path": str(path.resolve()),
            "cleared": clear_path(path),
        }
    return {
        "kind": "cache_clear",
        "work_root": str(work_root().resolve()),
        "cache_root": str(cache_root().resolve()),
        "scopes": normalized_scopes,
        "results": cleared,
    }


def format_cache_payload(payload: dict[str, object]) -> str:
    kind = str(payload.get("kind"))
    lines = [f"kind={kind}", f"work_root={payload['work_root']}", f"cache_root={payload['cache_root']}"]
    if kind == "cache_inspect":
        for section_name in ("inputs", "export_and_scan", "reusable_steps", "tmp"):
            section = payload.get(section_name, {})
            if not isinstance(section, dict):
                continue
            lines.append(f"[{section_name}]")
            for key, value in section.items():
                if isinstance(value, dict):
                    for nested_key, nested_value in value.items():
                        lines.append(f"{section_name}.{key}.{nested_key}={nested_value}")
                else:
                    lines.append(f"{section_name}.{key}={value}")
    elif kind == "cache_prune":
        for section_name in ("inputs", "export_and_scan", "reusable_steps"):
            section = payload.get(section_name, {})
            if not isinstance(section, dict):
                continue
            lines.append(f"[{section_name}]")
            for key, value in section.items():
                lines.append(f"{section_name}.{key}={value}")
    elif kind == "cache_clear":
        lines.append(f"scopes={','.join(payload.get('scopes', []))}")
        results = payload.get("results", {})
        if isinstance(results, dict):
            for scope, value in results.items():
                if not isinstance(value, dict):
                    continue
                lines.append(f"[{scope}]")
                for key, item in value.items():
                    lines.append(f"{scope}.{key}={item}")
    return "\n".join(lines)


def main() -> None:
    args = parse_args()
    if args.command == "cache":
        if args.cache_command == "inspect":
            payload = build_cache_inspect_payload()
        elif args.cache_command == "prune":
            payload = build_cache_prune_payload()
        else:
            payload = build_cache_clear_payload(args.scope)
        if args.format == "json":
            print(json.dumps(payload, ensure_ascii=False, indent=2))
        else:
            print(format_cache_payload(payload))
        return

    task_type = args.task_type

    try:
        inputs = load_input_json(args.input_json)
        plan = build_plan(
            task_type=task_type,
            inputs=inputs,
            artifact_root=args.artifact_root,
        )
    except PlannerError as error:
        if args.command == "plan":
            print(json.dumps(build_plan_error_payload(task_type, error), ensure_ascii=False, indent=2))
            raise SystemExit(1 if error.status in {"input_error", "execution_error"} else 0)

        run_id = make_run_id()
        run_root = ensure_run_root(run_id=run_id, artifact_root=args.artifact_root).resolve()
        result = build_analysis_error_result(
            run_id=run_id,
            task_type=task_type,
            artifact_root=str(run_root),
            status=error.status,
            message=error.message,
            limits=[],
        )
        primary_inputs = collect_input_paths(inputs.get("input")) if "inputs" in locals() else []
        timestamp = datetime.now(timezone.utc).isoformat()
        persist_run_outputs(
            run_root=run_root,
            run_result=result,
            plan={
                "task_type": task_type,
                "inputs": inputs if "inputs" in locals() and isinstance(inputs, dict) else {},
                "steps": [],
            },
            primary_inputs=primary_inputs,
            release_tag=None,
            started_at=timestamp,
            finished_at=timestamp,
        )
        print(json.dumps(result, ensure_ascii=False, indent=2))
        raise SystemExit(1 if error.status in {"input_error", "execution_error"} else 0)

    if args.command == "plan":
        print(json.dumps(plan, ensure_ascii=False, indent=2))
        return

    run_id = make_run_id()
    result = run_plan(plan, artifact_root=args.artifact_root, run_id=run_id)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    raise SystemExit(0 if result["status"] in {"ok", "empty", "ambiguous", "unsupported"} else 1)


if __name__ == "__main__":
    main()

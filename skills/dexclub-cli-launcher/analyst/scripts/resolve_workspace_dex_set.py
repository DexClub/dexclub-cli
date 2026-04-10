#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path

from dex_path_order import sort_dex_paths
from helper_cli import ActionableArgumentParser, format_actionable_error, require_existing_file


def parse_args() -> argparse.Namespace:
    parser = ActionableArgumentParser(
        description="Resolve the dex containing a target class from an existing workspace dex set.",
        formatter_class=argparse.RawTextHelpFormatter,
        epilog=(
            "Internal helper contract:\n"
            "  resolve_workspace_dex_set.py --input-dex ./artifacts/classes.dex "
            "--input-dex ./artifacts/classes2.dex --class com.example.Target --output-format json\n"
            "\n"
            "For stable analyst workflows, prefer `analyze.py run` with "
            "`{\"input\":{\"dex_dir\":...}}` or `{\"input\":{\"dex_list\":[...]}}`."
        ),
        error_summary="invalid resolve_workspace_dex_set.py arguments",
        recommended_action=(
            "Pass one or more existing `.dex` files with `--input-dex`, "
            "or switch to `analyze.py run` for the stable analyst entry."
        ),
    )
    parser.add_argument(
        "--input-dex",
        action="append",
        dest="input_dex_paths",
        required=True,
        help="Path to one dex file. Repeat for every dex in the set.",
    )
    parser.add_argument("--class", "--class-name", dest="class_name", required=True, help="Target class name.")
    parser.add_argument(
        "--output-format",
        "--format",
        dest="output_format",
        choices=("text", "json"),
        default="json",
        help="Output format.",
    )
    return parser.parse_args()


def normalize_input_dex_paths(raw_paths: list[str]) -> list[str]:
    deduped_paths: set[str] = set()
    for raw_path in raw_paths:
        deduped_paths.add(
            str(
                require_existing_file(
                    raw_path,
                    field_label="dex input",
                    recommended_action=(
                        "Provide one or more existing `.dex` files. "
                        "If you only have an APK, use `analyze.py run` with the APK input instead."
                    ),
                    allowed_suffixes=(".dex",),
                ).resolve()
            )
        )
    normalized = sort_dex_paths(list(deduped_paths))
    if not normalized:
        raise SystemExit(
            format_actionable_error(
                summary="resolve_workspace_dex_set.py requires at least one dex input",
                cause="No readable `.dex` files were provided after normalization.",
                recommended_action="Pass one or more `.dex` files with `--input-dex`.",
            )
        )
    return normalized


def build_run_find_command(input_dex_paths: list[str], class_name: str) -> list[str]:
    run_find_path = Path(__file__).resolve().parent / "run_find.py"
    command = [
        sys.executable,
        str(run_find_path),
        "class",
        "--output-format",
        "json",
        "--class-name",
        class_name,
        "--match-type",
        "Equals",
        "--no-ignore-case",
    ]
    for input_path in input_dex_paths:
        command.extend(["--input", input_path])
    return command


def normalize_payload(*, class_name: str, input_dex_paths: list[str], raw_items: object) -> dict[str, object]:
    if not isinstance(raw_items, list):
        raise SystemExit(
            format_actionable_error(
                summary="resolve_workspace_dex_set.py received an unexpected query payload",
                cause="`run_find.py class` did not return a JSON array.",
                recommended_action="Retry the same command and inspect the helper stdout/stderr for raw details.",
            )
        )
    candidate_paths = sort_dex_paths(
        list(
            {
                str(Path(item["sourceDexPath"]).resolve())
                for item in raw_items
                if isinstance(item, dict)
                and isinstance(item.get("sourceDexPath"), str)
                and item.get("sourceDexPath")
            }
        )
    )
    status = "empty"
    resolved_dex_path = None
    diagnostics: dict[str, object] = {}
    if len(candidate_paths) == 1:
        status = "ok"
        resolved_dex_path = candidate_paths[0]
    elif len(candidate_paths) > 1:
        status = "ambiguous"
        diagnostics = {
            "cause": "Target class matched more than one dex file in the provided workspace dex set.",
            "next_action": "Narrow the dex set or pass one direct dex input for summarize/export workflows.",
        }
    else:
        diagnostics = {
            "cause": "Target class was not found in the provided workspace dex set.",
            "next_action": "Check the class name or pass a dex set that actually contains the target class.",
        }
    return {
        "status": status,
        "class_name": class_name,
        "input_dex_paths": input_dex_paths,
        "candidate_dex_paths": candidate_paths,
        "resolved_dex_path": resolved_dex_path,
        "lookup_strategy": "workspace_dex_set_direct_query",
        "cache_hit": False,
        "diagnostics": diagnostics,
    }


def format_text(payload: dict[str, object]) -> str:
    lines = [
        f"class_name={payload['class_name']}",
        f"status={payload['status']}",
        f"candidate_count={len(payload['candidate_dex_paths'])}",
        f"lookup_strategy={payload.get('lookup_strategy')}",
    ]
    if payload.get("resolved_dex_path"):
        lines.append(f"resolved_dex_path={payload['resolved_dex_path']}")
    for dex_path in payload.get("candidate_dex_paths", []):
        lines.append(f"candidate_dex_path={dex_path}")
    diagnostics = payload.get("diagnostics")
    if isinstance(diagnostics, dict):
        cause = diagnostics.get("cause")
        next_action = diagnostics.get("next_action")
        if cause:
            lines.append(f"cause={cause}")
        if next_action:
            lines.append(f"next_action={next_action}")
    return "\n".join(lines)


def main() -> None:
    args = parse_args()
    input_dex_paths = normalize_input_dex_paths(args.input_dex_paths)
    command = build_run_find_command(input_dex_paths, args.class_name)
    completed = subprocess.run(command, capture_output=True, text=True)
    if completed.returncode != 0:
        raise SystemExit(
            format_actionable_error(
                summary="resolve_workspace_dex_set.py execution failed",
                cause=completed.stderr.strip() or f"run_find.py exited with code {completed.returncode}.",
                recommended_action=(
                    "Check whether the dex inputs are valid and whether the class name is exact. "
                    "For broader workflows, retry through `analyze.py run`."
                ),
            )
        )
    try:
        raw_payload = json.loads(completed.stdout)
    except json.JSONDecodeError as exc:
        raise SystemExit(
            format_actionable_error(
                summary="resolve_workspace_dex_set.py received malformed JSON",
                cause=f"run_find.py returned invalid JSON: {exc.msg}",
                recommended_action="Retry the same command and inspect the raw helper stdout/stderr.",
            )
        ) from exc
    payload = normalize_payload(
        class_name=args.class_name,
        input_dex_paths=input_dex_paths,
        raw_items=raw_payload,
    )
    if args.output_format == "json":
        print(json.dumps(payload, ensure_ascii=False, indent=2))
    else:
        print(format_text(payload))


if __name__ == "__main__":
    main()

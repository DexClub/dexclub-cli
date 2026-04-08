#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import subprocess
import sys
import tempfile
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from plan_schema import RUN_ARTIFACT_ROOT_PLACEHOLDER, SCHEMA_VERSION, TASK_REGISTRY

RUN_ARTIFACTS_DIRNAME = "dexclub-analyst-runs"
RUN_STATUS_OK = {"ok", "empty", "ambiguous", "unsupported"}


def make_run_id() -> str:
    stamp = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H-%M-%SZ")
    return f"{stamp}-{uuid.uuid4().hex[:6]}"


def ensure_run_root(*, run_id: str, artifact_root: str | None = None) -> Path:
    if artifact_root:
        root = Path(artifact_root).expanduser().resolve()
    else:
        root = Path(tempfile.gettempdir(), RUN_ARTIFACTS_DIRNAME, run_id).resolve()
    root.mkdir(parents=True, exist_ok=True)
    root.joinpath("exports").mkdir(parents=True, exist_ok=True)
    root.joinpath("results").mkdir(parents=True, exist_ok=True)
    return root


def write_json(path: Path, payload: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def build_analysis_error_result(
    *,
    run_id: str,
    task_type: str,
    artifact_root: str,
    status: str,
    message: str,
    plan: dict[str, object] | None = None,
    limits: list[str] | None = None,
    recommendations: list[dict[str, object]] | None = None,
) -> dict[str, object]:
    return {
        "schema_version": SCHEMA_VERSION,
        "run_id": run_id,
        "status": status,
        "task_type": task_type,
        "artifact_root": artifact_root,
        "plan": plan or {},
        "step_results": [],
        "summary": {
            "text": message,
            "style": "error" if status in {"input_error", "execution_error"} else status,
        },
        "artifacts": [{"type": "run_root", "path": artifact_root, "produced_by_step": "run"}],
        "recommendations": recommendations or [],
        "limits": limits or [],
        "evidence": [],
    }


def replace_placeholder(value: object, artifact_root: str) -> object:
    if isinstance(value, str):
        return value.replace(RUN_ARTIFACT_ROOT_PLACEHOLDER, artifact_root)
    if isinstance(value, list):
        return [replace_placeholder(item, artifact_root) for item in value]
    if isinstance(value, dict):
        return {key: replace_placeholder(item, artifact_root) for key, item in value.items()}
    return value


def extract_json_payload(stdout: str) -> object:
    lines = stdout.splitlines()
    for start in range(len(lines)):
        candidate = "\n".join(lines[start:]).strip()
        if not candidate or candidate[0] not in "[{":
            continue
        try:
            return json.loads(candidate)
        except json.JSONDecodeError:
            continue
    stripped = stdout.strip()
    if stripped and stripped[0] in "[{":
        return json.loads(stripped)
    raise ValueError("Unable to locate a JSON payload in command stdout.")


def normalize_run_find_items(raw_items: object) -> list[dict[str, object]]:
    if not isinstance(raw_items, list):
        raise ValueError("Expected list payload from run_find.")
    normalized: list[dict[str, object]] = []
    for item in raw_items:
        if not isinstance(item, dict):
            continue
        normalized_item: dict[str, object] = {}
        if "className" in item:
            normalized_item["class_name"] = item["className"]
        if "name" in item:
            normalized_item["method_name"] = item["name"]
        if "descriptor" in item:
            normalized_item["descriptor"] = item["descriptor"]
        if "sourceDexPath" in item:
            normalized_item["source_dex_path"] = item["sourceDexPath"]
        normalized.append(normalized_item)
    return normalized


def normalize_export_and_scan_payload(raw_payload: object) -> dict[str, object]:
    if not isinstance(raw_payload, dict):
        raise ValueError("Expected object payload from export_and_scan.")
    normalized: dict[str, object] = {}
    key_map = {
        "exportPath": "export_path",
        "branchLineCount": "branch_line_count",
        "returnLineCount": "return_line_count",
        "stringCount": "string_count",
        "numberCount": "number_count",
        "methodCallCount": "method_call_count",
        "fieldAccessCount": "field_access_count",
        "methodCalls": "method_calls",
        "fieldAccesses": "field_accesses",
    }
    for key, value in raw_payload.items():
        if key == "scope" and isinstance(value, dict):
            scope: dict[str, object] = {}
            for scope_key, scope_value in value.items():
                scope_key_map = {
                    "lineCount": "line_count",
                    "startLine": "start_line",
                    "endLine": "end_line",
                }
                scope[scope_key_map.get(scope_key, scope_key)] = scope_value
            normalized["scope"] = scope
            continue
        normalized[key_map.get(key, key)] = value
    return normalized


def build_run_find_command(step_args: dict[str, object], *, limit: int | None) -> list[str]:
    script_path = Path(__file__).resolve().parent / "run_find.py"
    command: list[str] = [sys.executable, str(script_path)]
    for input_path in step_args["inputs"]:
        command.extend(["--input", str(input_path)])
    command.extend(["--output-format", "json"])
    if limit is not None:
        command.extend(["--limit", str(limit)])
    command.append(str(step_args["kind"]))
    if step_args.get("declared_class"):
        command.extend(["--declared-class", str(step_args["declared_class"])])
    for package_name in step_args.get("search_package", []):
        command.extend(["--search-package", str(package_name)])
    for value in step_args.get("using_string", []):
        command.extend(["--using-string", str(value)])
    for value in step_args.get("using_number", []):
        command.extend(["--using-number", str(value)])
    for value in step_args.get("invoke_method", []):
        command.extend(["--invoke-method", str(value)])
    for value in step_args.get("caller_method", []):
        command.extend(["--caller-method", str(value)])
    if step_args.get("method_name"):
        command.extend(["--method-name", str(step_args["method_name"])])
    return command


def build_export_and_scan_command(step_args: dict[str, object]) -> list[str]:
    script_path = Path(__file__).resolve().parent / "export_and_scan.py"
    command = [
        sys.executable,
        str(script_path),
        "--input-dex",
        str(step_args["input_dex"]),
        "--class",
        str(step_args["class_name"]),
        "--language",
        str(step_args["language"]),
        "--mode",
        str(step_args["mode"]),
        "--format",
        "json",
        "--output-dir",
        str(step_args["output_dir"]),
    ]
    if step_args.get("method"):
        command.extend(["--method", str(step_args["method"])])
    return command


def determine_limit(task_type: str, step_args: dict[str, object]) -> tuple[int | None, int | None]:
    task_definition = TASK_REGISTRY[task_type]
    external_limit = step_args.get("limit")
    if external_limit is not None and isinstance(external_limit, int):
        presentation_limit = min(task_definition.max_direct_hits or external_limit, external_limit)
    elif task_definition.max_direct_hits is not None:
        presentation_limit = task_definition.max_direct_hits
    else:
        presentation_limit = None

    if task_definition.max_direct_hits is None:
        return presentation_limit, external_limit if isinstance(external_limit, int) else None

    internal_limit = task_definition.max_direct_hits + 1
    if isinstance(external_limit, int):
        internal_limit = max(internal_limit, external_limit + 1)
    return presentation_limit, internal_limit


def infer_narrow_search_filters(items: list[dict[str, object]], step_args: dict[str, object]) -> dict[str, object]:
    filters: dict[str, object] = {}
    if step_args.get("search_package"):
        filters["search_package"] = list(step_args["search_package"])
    class_names = sorted({str(item.get("class_name")) for item in items if item.get("class_name")})
    if len(class_names) == 1:
        filters["declared_class"] = class_names[0]
    elif not filters and class_names:
        package_candidates = sorted({".".join(name.split(".")[:-1]) for name in class_names if "." in name})
        if package_candidates:
            filters["search_package"] = package_candidates[:3]
    return filters


def build_hit_evidence(*, step_id: str, items: list[dict[str, object]], kind: str) -> list[dict[str, object]]:
    evidence: list[dict[str, object]] = []
    for item in items:
        descriptor = item.get("descriptor")
        value = descriptor or f"{item.get('class_name')}#{item.get('method_name')}"
        entry: dict[str, object] = {
            "step_id": step_id,
            "kind": kind,
            "value": value,
        }
        if item.get("source_dex_path"):
            entry["source_path"] = item["source_dex_path"]
        evidence.append(entry)
    return evidence


def build_export_evidence(step_id: str, payload: dict[str, object]) -> list[dict[str, object]]:
    evidence: list[dict[str, object]] = []
    export_path = payload.get("export_path")
    for evidence_kind, key in (
        ("string_hit", "strings"),
        ("number_hit", "numbers"),
        ("method_call_hit", "method_calls"),
        ("field_access_hit", "field_accesses"),
    ):
        items = payload.get(key)
        if not isinstance(items, list):
            continue
        for item in items:
            if not isinstance(item, dict):
                continue
            entry: dict[str, object] = {
                "step_id": step_id,
                "kind": evidence_kind,
                "value": item.get("value"),
            }
            if export_path:
                entry["source_path"] = export_path
            if isinstance(item.get("lines"), list):
                entry["line_numbers"] = item["lines"]
            evidence.append(entry)
    return evidence


def count_method_overloads(export_path: str, method_name: str) -> int:
    path = Path(export_path)
    text = path.read_text(encoding="utf-8")
    if path.suffix.lower() == ".smali":
        pattern = re.compile(rf"^\.method\b.*\b{re.escape(method_name)}\(", re.MULTILINE)
        return len(pattern.findall(text))
    pattern = re.compile(rf"\b{re.escape(method_name)}\s*\(")
    return len(pattern.findall(text))


def normalize_run_find_result(
    *,
    task_type: str,
    step_id: str,
    step_args: dict[str, object],
    anchor: dict[str, object] | None,
    payload: object,
) -> tuple[str, dict[str, object], list[dict[str, object]], list[dict[str, object]], list[str]]:
    normalized_items = normalize_run_find_items(payload)
    presentation_limit, _ = determine_limit(task_type, step_args)
    task_definition = TASK_REGISTRY[task_type]
    threshold_overflow = (
        task_definition.max_direct_hits is not None
        and len(normalized_items) > task_definition.max_direct_hits
    )
    if presentation_limit is not None:
        visible_items = normalized_items[:presentation_limit]
    else:
        visible_items = normalized_items
    truncated = threshold_overflow or len(visible_items) < len(normalized_items)
    recommendations: list[dict[str, object]] = []
    limits: list[str] = []

    if task_definition.result_shape == "relation":
        result: dict[str, object] = {
            "relation_direction": task_definition.relation_direction,
            "anchor": anchor or {},
            "count": len(visible_items),
            "items": visible_items,
        }
    else:
        result = {
            "count": len(visible_items),
            "items": visible_items,
        }
    if truncated:
        result["truncated"] = True

    if threshold_overflow:
        recommendations.append(
            {
                "kind": "narrow_search",
                "message": "Too many direct hits. Narrow by package or declaring class.",
                "reason": "max_direct_hits_exceeded",
                "suggested_filters": infer_narrow_search_filters(normalized_items, step_args),
            }
        )
        limits.append("direct hits exceeded max_direct_hits; result preview is truncated")

    if not visible_items:
        status = "empty"
    else:
        status = "ok"

    evidence_kind = "relation_hit" if task_definition.result_shape == "relation" else "method_hit"
    evidence = build_hit_evidence(step_id=step_id, items=visible_items, kind=evidence_kind)
    return status, result, recommendations, evidence, limits


def normalize_export_result(
    *,
    step_id: str,
    payload: object,
) -> tuple[str, dict[str, object], list[dict[str, object]], list[dict[str, object]], list[str]]:
    normalized_payload = normalize_export_and_scan_payload(payload)
    evidence = build_export_evidence(step_id, normalized_payload)
    return "ok", normalized_payload, [], evidence, []


def execute_step(
    *,
    task_type: str,
    step: dict[str, object],
    artifact_root: Path,
    anchor: dict[str, object] | None = None,
) -> tuple[dict[str, object], list[dict[str, object]], list[dict[str, object]], list[str]]:
    step_id = str(step["step_id"])
    step_args = replace_placeholder(dict(step["args"]), str(artifact_root))
    if not isinstance(step_args, dict):
        raise ValueError("Step args must remain a mapping after placeholder replacement.")

    if step["kind"] == "run_find":
        _, internal_limit = determine_limit(task_type, step_args)
        command = build_run_find_command(step_args, limit=internal_limit)
    elif step["kind"] == "export_and_scan":
        command = build_export_and_scan_command(step_args)
    else:
        raise ValueError(f"Unsupported step kind: {step['kind']}")

    completed = subprocess.run(command, capture_output=True, text=True)
    step_artifacts: list[dict[str, object]] = []
    recommendations: list[dict[str, object]] = []
    evidence: list[dict[str, object]] = []
    extra_limits: list[str] = []

    step_result: dict[str, object] = {
        "step_id": step_id,
        "step_kind": step["kind"],
        "status": "execution_error" if completed.returncode else "empty",
        "exit_code": completed.returncode,
        "command": command,
        "stdout": completed.stdout,
        "stderr": completed.stderr,
        "artifacts": step_artifacts,
        "result": {},
    }

    if completed.returncode != 0:
        return step_result, recommendations, evidence, extra_limits

    try:
        payload = extract_json_payload(completed.stdout)
        if step["kind"] == "run_find":
            status, normalized_result, recommendations, evidence, extra_limits = normalize_run_find_result(
                task_type=task_type,
                step_id=step_id,
                step_args=step_args,
                anchor=anchor,
                payload=payload,
            )
        else:
            status, normalized_result, recommendations, evidence, extra_limits = normalize_export_result(
                step_id=step_id,
                payload=payload,
            )
        step_result["status"] = status
        step_result["result"] = normalized_result
    except Exception as exc:
        step_result["status"] = "execution_error"
        step_result["stderr"] = (completed.stderr + f"\nNormalization error: {exc}").strip()
        return step_result, recommendations, evidence, extra_limits

    step_result_path = artifact_root / "results" / f"{step_id}.json"
    write_json(step_result_path, step_result)
    step_artifacts.append(
        {
            "type": "step_result",
            "path": str(step_result_path.resolve()),
            "produced_by_step": step_id,
        }
    )

    if step["kind"] == "export_and_scan":
        export_path = step_result["result"].get("export_path")
        if isinstance(export_path, str):
            step_artifacts.append(
                {
                    "type": "exported_code",
                    "path": export_path,
                    "produced_by_step": step_id,
                }
            )
    return step_result, recommendations, evidence, extra_limits


def finalize_run_result(
    *,
    run_id: str,
    plan: dict[str, object],
    artifact_root: Path,
    step_results: list[dict[str, object]],
    artifacts: list[dict[str, object]],
    recommendations: list[dict[str, object]],
    evidence: list[dict[str, object]],
    limits: list[str],
) -> dict[str, object]:
    task_type = str(plan["task_type"])
    status = "empty"
    summary_text = "No results."
    summary_style = "empty"

    execution_failure = next((step for step in step_results if step["status"] == "execution_error"), None)
    if execution_failure is not None:
        status = "execution_error"
        summary_text = f"Step `{execution_failure['step_id']}` failed during execution."
        summary_style = "error"
    elif task_type == "summarize_method_logic":
        step_result = step_results[0]
        if step_result["status"] == "ok":
            export_path = step_result["result"].get("export_path")
            method_anchor = plan["inputs"].get("method_anchor", {})
            if isinstance(export_path, str) and isinstance(method_anchor, dict):
                overload_count = count_method_overloads(export_path, str(method_anchor.get("method_name")))
                if overload_count > 1:
                    status = "ambiguous"
                    summary_text = (
                        f"Exported class contains {overload_count} overloads for "
                        f"`{method_anchor.get('method_name')}`; current path cannot isolate one precisely."
                    )
                    summary_style = "ambiguous"
                    recommendations.append(
                        {
                            "kind": "narrow_search",
                            "message": "Choose a method name that is unique within the exported class in version 1.",
                            "reason": "overload_ambiguity",
                        }
                    )
                    limits.append("export-and-scan cannot disambiguate overloaded methods by descriptor")
                else:
                    status = "ok"
                    summary_text = "Summarized one exported method body."
                    summary_style = "partial_support"
            else:
                status = "execution_error"
                summary_text = "Export step did not surface an export path."
                summary_style = "error"
        else:
            status = "empty"
            summary_text = "No exported method summary was produced."
            summary_style = "empty"
    else:
        step_result = step_results[0] if step_results else None
        result_payload = step_result["result"] if step_result else {}
        count = result_payload.get("count", 0) if isinstance(result_payload, dict) else 0
        truncated = bool(result_payload.get("truncated")) if isinstance(result_payload, dict) else False
        if step_result and step_result["status"] == "ok":
            status = "ok"
            if task_type == "trace_callers":
                summary_text = f"Found {count} direct callers."
            elif task_type == "trace_callees":
                summary_text = f"Found {count} direct callees."
            else:
                summary_text = f"Found {count} matching methods."
            if truncated:
                summary_text += " Preview is truncated."
            summary_style = "partial_support"
        else:
            status = "empty"
            if task_type == "trace_callers":
                summary_text = "No direct callers found."
            elif task_type == "trace_callees":
                summary_text = "No direct callees found."
            else:
                summary_text = "No matching methods found."
            summary_style = "empty"

    return {
        "schema_version": SCHEMA_VERSION,
        "run_id": run_id,
        "status": status,
        "task_type": task_type,
        "artifact_root": str(artifact_root.resolve()),
        "plan": plan,
        "step_results": step_results,
        "summary": {
            "text": summary_text,
            "style": summary_style,
        },
        "artifacts": artifacts,
        "recommendations": recommendations,
        "limits": limits,
        "evidence": evidence,
    }


def run_plan(plan: dict[str, object], *, artifact_root: str | None = None, run_id: str | None = None) -> dict[str, object]:
    current_run_id = run_id or make_run_id()
    run_root = ensure_run_root(run_id=current_run_id, artifact_root=artifact_root)
    rewritten_plan = replace_placeholder(plan, str(run_root))
    if not isinstance(rewritten_plan, dict):
        raise ValueError("Plan must remain a mapping after placeholder replacement.")

    task_type = str(rewritten_plan["task_type"])
    step_results: list[dict[str, object]] = []
    recommendations: list[dict[str, object]] = []
    evidence: list[dict[str, object]] = []
    limits = list(rewritten_plan.get("limits", []))
    artifacts: list[dict[str, object]] = [
        {"type": "run_root", "path": str(run_root.resolve()), "produced_by_step": "run"}
    ]

    for step in rewritten_plan.get("steps", []):
        relation_anchor = None
        if isinstance(step, dict) and task_type in {"trace_callers", "trace_callees"}:
            method_anchor = rewritten_plan["inputs"].get("method_anchor", {})
            if isinstance(method_anchor, dict):
                relation_anchor = method_anchor
        step_result, step_recommendations, step_evidence, extra_limits = execute_step(
            task_type=task_type,
            step=step,
            artifact_root=run_root,
            anchor=relation_anchor,
        )
        step_results.append(step_result)
        recommendations.extend(step_recommendations)
        evidence.extend(step_evidence)
        limits.extend(extra_limits)
        artifacts.extend(step_result["artifacts"])

        if step_result["status"] == "execution_error":
            break

    final_result = finalize_run_result(
        run_id=current_run_id,
        plan=rewritten_plan,
        artifact_root=run_root,
        step_results=step_results,
        artifacts=artifacts,
        recommendations=recommendations,
        evidence=evidence,
        limits=limits,
    )
    final_result_path = run_root / "results" / "final_result.json"
    write_json(final_result_path, final_result)
    return final_result

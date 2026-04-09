#!/usr/bin/env python3
from __future__ import annotations

import json
import hashlib
import os
import re
import subprocess
import sys
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from analyst_storage import (
    CachedInputRef,
    detect_cached_input,
    ensure_apk_input_cache,
    ensure_default_run_root,
    ensure_dex_input_cache,
    filesystem_lock,
    inputs_cache_root,
    runs_root,
)
from process_exec import extract_json_payload, run_captured_process
from output_contract import validate_latest_index, validate_run_summary, validate_step_result_envelope
from plan_schema import RUN_ARTIFACT_ROOT_PLACEHOLDER, SCHEMA_VERSION, TASK_REGISTRY

RUN_STATUS_OK = {"ok", "empty", "ambiguous", "unsupported"}
RUN_SUMMARY_PREFERRED = {"ok", "partial"}
STEP_REUSE_INDEX_FILE_NAME = "reusable-step-index-v1.json"


def camel_to_snake(name: str) -> str:
    first_pass = re.sub("(.)([A-Z][a-z]+)", r"\1_\2", name)
    return re.sub("([a-z0-9])([A-Z])", r"\1_\2", first_pass).lower()


def make_run_id() -> str:
    stamp = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H-%M-%SZ")
    return f"{stamp}-{uuid.uuid4().hex[:6]}"


def ensure_run_root(*, run_id: str, artifact_root: str | None = None) -> Path:
    if artifact_root:
        root = Path(artifact_root).expanduser().resolve()
    else:
        root = ensure_default_run_root(run_id).resolve()
    root.mkdir(parents=True, exist_ok=True)
    root.joinpath("steps").mkdir(parents=True, exist_ok=True)
    return root


def write_json(path: Path, payload: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp_path = path.parent / f".{path.name}.tmp-{uuid.uuid4().hex[:8]}"
    try:
        tmp_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        tmp_path.replace(path)
    finally:
        if tmp_path.exists():
            tmp_path.unlink()


def write_text(path: Path, content: str) -> int:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    return len(content.encode("utf-8"))


def ensure_step_root(run_root: Path, step_id: str) -> Path:
    step_root = run_root / "steps" / step_id
    step_root.mkdir(parents=True, exist_ok=True)
    step_root.joinpath("artifacts").mkdir(parents=True, exist_ok=True)
    return step_root


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
        "cache_root": str(inputs_cache_root().resolve()),
        "temporary_paths": [],
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


def add_artifact(
    artifacts: list[dict[str, object]],
    *,
    artifact_type: str,
    path: str,
    produced_by_step: str,
) -> None:
    if any(item.get("type") == artifact_type and item.get("path") == path for item in artifacts):
        return
    artifacts.append(
        {
            "type": artifact_type,
            "path": path,
            "produced_by_step": produced_by_step,
        }
    )


def deep_copy_mapping(value: dict[str, object]) -> dict[str, object]:
    return json.loads(json.dumps(value, ensure_ascii=False))


def stable_hash(payload: dict[str, object]) -> str:
    encoded = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def replace_placeholder(value: object, artifact_root: str) -> object:
    if isinstance(value, str):
        return value.replace(RUN_ARTIFACT_ROOT_PLACEHOLDER, artifact_root)
    if isinstance(value, list):
        return [replace_placeholder(item, artifact_root) for item in value]
    if isinstance(value, dict):
        return {key: replace_placeholder(item, artifact_root) for key, item in value.items()}
    return value


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
        "cacheHit": "cache_hit",
        "cacheKey": "cache_key",
        "cachePath": "cache_path",
        "exportScanCacheRoot": "export_scan_cache_root",
        "branchLineCount": "branch_line_count",
        "returnLineCount": "return_line_count",
        "stringCount": "string_count",
        "numberCount": "number_count",
        "methodCallCount": "method_call_count",
        "fieldAccessCount": "field_access_count",
        "methodCalls": "method_calls",
        "fieldAccesses": "field_accesses",
        "branchHotspots": "branch_hotspots",
        "structuredSummary": "structured_summary",
        "methodDescriptor": "method_descriptor",
    }
    for key, value in raw_payload.items():
        if key == "scope" and isinstance(value, dict):
            scope: dict[str, object] = {}
            for scope_key, scope_value in value.items():
                scope_key_map = {
                    "lineCount": "line_count",
                    "startLine": "start_line",
                    "endLine": "end_line",
                    "methodDescriptor": "method_descriptor",
                }
                scope[scope_key_map.get(scope_key, scope_key)] = scope_value
            normalized["scope"] = scope
            continue
        if key == "largeMethodAnalysis" and isinstance(value, dict):
            normalized["large_method_analysis"] = normalize_nested_object_keys(value)
            continue
        if key == "structuredSummary" and isinstance(value, dict):
            normalized["structured_summary"] = normalize_nested_object_keys(value)
            continue
        normalized[key_map.get(key, key)] = value
    return normalized


def normalize_nested_object_keys(value: object) -> object:
    if isinstance(value, list):
        return [normalize_nested_object_keys(item) for item in value]
    if isinstance(value, dict):
        return {
            camel_to_snake(str(key)): normalize_nested_object_keys(item)
            for key, item in value.items()
        }
    return value


def normalize_resolve_apk_dex_payload(raw_payload: object) -> dict[str, object]:
    if not isinstance(raw_payload, dict):
        raise ValueError("Expected object payload from resolve_apk_dex.")
    normalized: dict[str, object] = {}
    for key in (
        "apk_path",
        "class_name",
        "artifact_root",
        "cache_root",
        "temporary_paths",
        "output_dir",
        "index_cache_path",
        "cache_hit",
        "lookup_strategy",
        "scanned_dex_count",
        "candidate_dex_paths",
        "extracted_dex_paths",
        "resolved_dex_path",
    ):
        if key in raw_payload:
            normalized[key] = raw_payload[key]
    return normalized


def build_run_find_command(step_args: dict[str, object], *, limit: int | None) -> list[str]:
    script_path = Path(__file__).resolve().parent / "run_find.py"
    command: list[str] = [sys.executable, str(script_path)]
    for input_path in step_args["inputs"]:
        command.extend(["--input", str(input_path)])
    command.extend(["--output-format", "json"])
    if step_args.get("raw_query_json") is not None:
        command.extend(["--raw-query-json", json.dumps(step_args["raw_query_json"], ensure_ascii=False)])
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
    if step_args.get("method_descriptor"):
        command.extend(["--method-descriptor", str(step_args["method_descriptor"])])
    return command


def build_resolve_apk_dex_command(step_args: dict[str, object]) -> list[str]:
    script_path = Path(__file__).resolve().parent / "resolve_apk_dex.py"
    command = [
        sys.executable,
        str(script_path),
        "--input-apk",
        str(step_args["input_apk"]),
        "--class",
        str(step_args["class_name"]),
        "--format",
        "json",
    ]
    output_dir = step_args.get("output_dir")
    if output_dir:
        command.extend(["--output-dir", str(output_dir)])
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


def normalize_resolve_apk_dex_result(
    *,
    step_id: str,
    payload: object,
) -> tuple[str, dict[str, object], list[dict[str, object]], list[dict[str, object]], list[str]]:
    normalized_payload = normalize_resolve_apk_dex_payload(payload)
    candidate_dex_paths = normalized_payload.get("candidate_dex_paths", [])
    if not isinstance(candidate_dex_paths, list):
        raise ValueError("`candidate_dex_paths` must be a list.")
    if normalized_payload.get("resolved_dex_path"):
        status = "ok"
    elif candidate_dex_paths:
        status = "ok"
    else:
        status = "empty"
    evidence: list[dict[str, object]] = []
    for candidate_path in candidate_dex_paths:
        evidence.append(
            {
                "step_id": step_id,
                "kind": "resolved_dex_candidate",
                "value": candidate_path,
                "source_path": candidate_path,
            }
        )
    return status, normalized_payload, [], evidence, []


def build_step_reuse_key(
    *,
    task_type: str,
    release_tag: str | None,
    step_kind: str,
    step_args: dict[str, object],
    step_cache_keys: list[str],
) -> str | None:
    if step_kind == "run_find":
        semantic_args = {
            "inputs": step_cache_keys or step_args.get("inputs"),
            "kind": step_args.get("kind"),
            "declared_class": step_args.get("declared_class"),
            "search_package": step_args.get("search_package"),
            "using_string": step_args.get("using_string"),
            "using_number": step_args.get("using_number"),
            "invoke_method": step_args.get("invoke_method"),
            "caller_method": step_args.get("caller_method"),
            "method_name": step_args.get("method_name"),
            "raw_query_json": step_args.get("raw_query_json"),
            "limit": step_args.get("limit"),
        }
    elif step_kind == "resolve_apk_dex":
        semantic_args = {
            "class_name": step_args.get("class_name"),
            "input_cache_keys": step_cache_keys,
            "input_apk": None if step_cache_keys else step_args.get("input_apk"),
        }
    elif step_kind == "export_and_scan":
        semantic_args = {
            "input_dex": step_args.get("input_dex"),
            "class_name": step_args.get("class_name"),
            "method": step_args.get("method"),
            "method_descriptor": step_args.get("method_descriptor"),
            "language": step_args.get("language"),
            "mode": step_args.get("mode"),
        }
    else:
        return None
    return stable_hash(
        {
            "schema_version": SCHEMA_VERSION,
            "task_type": task_type,
            "release_tag": release_tag or "",
            "step_kind": step_kind,
            "args": semantic_args,
        }
    )


def resolve_step_arguments(
    step_args: dict[str, object],
    *,
    prior_step_results: dict[str, dict[str, object]],
) -> dict[str, object]:
    resolved_args = dict(step_args)
    source_step_id = resolved_args.pop("input_dex_from_step", None)
    if not source_step_id:
        return resolved_args
    if not isinstance(source_step_id, str):
        raise ValueError("`input_dex_from_step` must be a step id string.")
    source_step_result = prior_step_results.get(source_step_id)
    if source_step_result is None:
        raise ValueError(f"Missing prior step result: {source_step_id}")
    resolved_dex_path = source_step_result.get("result", {}).get("resolved_dex_path")
    if not isinstance(resolved_dex_path, str) or not resolved_dex_path:
        raise ValueError(f"Prior step `{source_step_id}` did not resolve a dex path.")
    resolved_args["input_dex"] = resolved_dex_path
    return resolved_args


def collect_input_paths(raw_input: object) -> list[str]:
    if isinstance(raw_input, str):
        return [str(Path(raw_input).expanduser().resolve())]
    if isinstance(raw_input, list):
        return [str(Path(item).expanduser().resolve()) for item in raw_input if isinstance(item, str)]
    return []


def register_cache_ref(
    cache_refs: dict[str, CachedInputRef],
    cache_ref: CachedInputRef | None,
) -> None:
    if cache_ref is None:
        return
    cache_refs[cache_ref.cache_key] = cache_ref


def cache_input_path(
    input_path: str,
    *,
    ensure_apk_extracted_dex: bool,
) -> tuple[str, CachedInputRef | None]:
    resolved_path = Path(input_path).expanduser().resolve()
    cached_ref = detect_cached_input(resolved_path)
    if cached_ref is not None:
        return str(resolved_path), cached_ref

    suffix = resolved_path.suffix.lower()
    if suffix == ".dex":
        dex_ref = ensure_dex_input_cache(resolved_path)
        cached_path = dex_ref.cached_path or resolved_path
        return str(cached_path.resolve()), dex_ref
    if suffix == ".apk":
        apk_ref = ensure_apk_input_cache(resolved_path, ensure_extracted_dex=ensure_apk_extracted_dex)
        return str(resolved_path), apk_ref
    return str(resolved_path), None


def prepare_step_args_for_storage(
    step_args: dict[str, object],
    *,
    step_kind: str,
    cache_refs: dict[str, CachedInputRef],
) -> tuple[dict[str, object], list[str]]:
    prepared_args = dict(step_args)
    step_cache_keys: list[str] = []

    if step_kind == "run_find":
        prepared_inputs: list[str] = []
        raw_inputs = prepared_args.get("inputs", [])
        if isinstance(raw_inputs, list):
            for input_path in raw_inputs:
                original_path = str(Path(str(input_path)).expanduser().resolve())
                _, cache_ref = cache_input_path(original_path, ensure_apk_extracted_dex=False)
                prepared_inputs.append(original_path)
                register_cache_ref(cache_refs, cache_ref)
                if cache_ref is not None:
                    step_cache_keys.append(cache_ref.cache_key)
        prepared_args["inputs"] = prepared_inputs
        return prepared_args, sorted(set(step_cache_keys))

    if step_kind == "resolve_apk_dex" and "input_apk" in prepared_args:
        input_apk = str(prepared_args["input_apk"])
        _, apk_ref = cache_input_path(input_apk, ensure_apk_extracted_dex=True)
        register_cache_ref(cache_refs, apk_ref)
        if apk_ref is not None:
            step_cache_keys.append(apk_ref.cache_key)
        if apk_ref is not None and apk_ref.extracted_dex_dir is not None:
            prepared_args["output_dir"] = str(apk_ref.extracted_dex_dir.resolve())
        return prepared_args, sorted(set(step_cache_keys))

    if step_kind == "export_and_scan" and "input_dex" in prepared_args:
        cached_path, cache_ref = cache_input_path(
            str(prepared_args["input_dex"]),
            ensure_apk_extracted_dex=False,
        )
        prepared_args["input_dex"] = cached_path
        register_cache_ref(cache_refs, cache_ref)
        if cache_ref is not None:
            step_cache_keys.append(cache_ref.cache_key)
        return prepared_args, sorted(set(step_cache_keys))

    return prepared_args, sorted(set(step_cache_keys))


def resolve_release_tag() -> str | None:
    override = os.environ.get("DEXCLUB_ANALYST_RELEASE_TAG_OVERRIDE")
    if override is not None:
        normalized = override.strip()
        return normalized or None
    launcher_dir = Path(__file__).resolve().parents[2] / "launcher" / "scripts"
    if sys.platform == "win32":
        command = ["cmd.exe", "/c", str((launcher_dir / "run_latest_release.bat").resolve()), "--print-latest-tag"]
    else:
        command = ["bash", str((launcher_dir / "run_latest_release.sh").resolve()), "--print-latest-tag"]
    try:
        completed = subprocess.run(command, capture_output=True, text=True, timeout=10)
    except (OSError, subprocess.TimeoutExpired):
        return None
    if completed.returncode != 0:
        return None
    tag = completed.stdout.strip()
    return tag or None


def build_run_meta(
    *,
    run_id: str,
    task_type: str,
    input_paths: list[str],
    cache_refs: dict[str, CachedInputRef],
    inputs: dict[str, object],
    release_tag: str | None,
    created_at: str,
) -> dict[str, object]:
    method_anchor = inputs.get("method_anchor")
    language = inputs.get("language")
    return {
        "run_id": run_id,
        "task_type": task_type,
        "input_paths": input_paths,
        "input_cache_keys": sorted(cache_refs),
        "method_anchor": method_anchor if isinstance(method_anchor, dict) else None,
        "language": language if isinstance(language, str) else None,
        "release_tag": release_tag,
        "created_at": created_at,
    }


def detect_input_source(primary_inputs: list[str]) -> str:
    suffixes = {Path(path).suffix.lower() for path in primary_inputs}
    if ".apk" in suffixes:
        return "apk_direct"
    return "workspace_dex_set"


def get_required_step_kinds(plan: dict[str, object]) -> set[str]:
    task_type = str(plan.get("task_type", ""))
    if task_type == "summarize_method_logic":
        step_kinds = {
            str(step.get("kind"))
            for step in plan.get("steps", [])
            if isinstance(step, dict) and isinstance(step.get("kind"), str)
        }
        if "resolve_apk_dex" in step_kinds:
            return {"resolve_apk_dex", "export_and_scan"}
        return {"export_and_scan"}
    if task_type in {
        "search_methods_by_string",
        "search_methods_by_number",
        "trace_callers",
        "trace_callees",
    }:
        return {"run_find"}
    return set()


def is_reusable_step(step_result: dict[str, object]) -> bool:
    if step_result.get("status") not in RUN_STATUS_OK:
        return False
    result = step_result.get("result", {})
    if not isinstance(result, dict):
        return False
    step_kind = step_result.get("step_kind")
    if step_kind == "resolve_apk_dex":
        candidate_dex_paths = result.get("candidate_dex_paths")
        return bool(result.get("resolved_dex_path")) or bool(candidate_dex_paths)
    if step_kind == "run_find":
        items = result.get("items")
        return isinstance(items, list) and len(items) > 0
    if step_kind == "export_and_scan":
        return bool(result.get("export_path"))
    return False


def is_run_successful(
    *,
    plan: dict[str, object],
    step_results: list[dict[str, object]],
    run_status: str,
) -> bool:
    task_type = str(plan.get("task_type", ""))
    if task_type in {"search_methods_by_string", "search_methods_by_number"}:
        return any(
            step.get("step_kind") == "run_find" and step.get("status") in {"ok", "empty"}
            for step in step_results
        )
    if task_type in {"trace_callers", "trace_callees"}:
        return any(step.get("step_kind") == "run_find" and step.get("status") == "ok" for step in step_results)
    if task_type == "summarize_method_logic":
        return run_status == "ok"
    return run_status == "ok"


def aggregate_run_summary_status(
    *,
    plan: dict[str, object],
    step_results: list[dict[str, object]],
    run_status: str,
) -> str:
    if run_status in {"input_error", "unsupported"}:
        return "input_error"
    if is_run_successful(plan=plan, step_results=step_results, run_status=run_status):
        return "ok"
    if any(is_reusable_step(step_result) for step_result in step_results):
        return "partial"
    if any(step_result.get("status") == "input_error" for step_result in step_results):
        return "input_error"
    return "execution_error"


def build_step_index(
    *,
    run_root: Path,
    step_results: list[dict[str, object]],
    required_step_kinds: set[str],
) -> list[dict[str, object]]:
    items: list[dict[str, object]] = []
    for step_result in step_results:
        step_id = str(step_result.get("step_id"))
        step_root = step_result.get("step_root")
        if not isinstance(step_root, str) or not step_root:
            step_root = str((run_root / "steps" / step_id).resolve())
        items.append(
            {
                "step_id": step_id,
                "step_kind": step_result.get("step_kind"),
                "status": step_result.get("status"),
                "step_root": step_root,
                "attempt_count": 1,
                "is_reusable_step": is_reusable_step(step_result),
                "is_required": step_result.get("step_kind") in required_step_kinds,
            }
        )
    return items


def build_key_artifacts(
    *,
    task_type: str,
    artifacts: list[dict[str, object]],
) -> list[dict[str, object]]:
    candidates: list[tuple[int, dict[str, object]]] = []
    seen_paths: set[str] = set()
    for artifact in artifacts:
        path = artifact.get("path")
        if not isinstance(path, str) or not path or path in seen_paths:
            continue
        artifact_type = artifact.get("type")
        if artifact_type == "exported_code":
            seen_paths.add(path)
            candidates.append(
                (
                    3,
                    {
                        "type": "exported_code",
                        "path": path,
                        "label": "导出的目标类代码",
                        "reason": "本次 run 已导出可直接复核的代码实体，适合作为接手入口",
                    },
                )
            )
        elif artifact_type == "resolved_dex":
            seen_paths.add(path)
            candidates.append(
                (
                    1 if task_type == "summarize_method_logic" else 2,
                    {
                        "type": "resolved_target",
                        "path": path,
                        "label": "已解析出的目标 dex",
                        "reason": "本次 run 已收敛到可直接复用的 dex 结果，后续会话可继续利用",
                    },
                )
            )
    candidates.sort(key=lambda item: item[0], reverse=True)
    return [item for _, item in candidates[:5]]


def build_run_summary_payload(
    *,
    run_root: Path,
    plan: dict[str, object],
    run_result: dict[str, object],
    primary_inputs: list[str],
    started_at: str,
    finished_at: str,
    updated_at: str,
) -> dict[str, object]:
    step_results = run_result.get("step_results", [])
    if not isinstance(step_results, list):
        step_results = []
    required_step_kinds = get_required_step_kinds(plan)
    run_summary_status = aggregate_run_summary_status(
        plan=plan,
        step_results=step_results,
        run_status=str(run_result.get("status", "execution_error")),
    )
    latest_step_id = next(
        (str(step.get("step_id")) for step in reversed(step_results) if step.get("step_id")),
        None,
    )
    latest_successful_step_id = next(
        (
            str(step.get("step_id"))
            for step in reversed(step_results)
            if step.get("step_id") and step.get("status") in RUN_STATUS_OK
        ),
        None,
    )
    summary_style = "ok" if run_summary_status == "ok" else "warning" if run_summary_status == "partial" else "error"
    return {
        "schema_version": SCHEMA_VERSION,
        "run_id": run_result["run_id"],
        "run_root": str(run_root.resolve()),
        "status": run_summary_status,
        "task_type": plan.get("task_type"),
        "input_source": detect_input_source(primary_inputs),
        "primary_inputs": primary_inputs,
        "started_at": started_at,
        "finished_at": finished_at,
        "updated_at": updated_at,
        "latest_step_id": latest_step_id,
        "latest_successful_step_id": latest_successful_step_id,
        "key_artifacts": build_key_artifacts(
            task_type=str(plan.get("task_type", "")),
            artifacts=run_result.get("artifacts", []) if isinstance(run_result.get("artifacts"), list) else [],
        ),
        "step_index": build_step_index(
            run_root=run_root,
            step_results=step_results,
            required_step_kinds=required_step_kinds,
        ),
        "summary": {
            "text": str(run_result.get("summary", {}).get("text", "")),
            "style": summary_style,
        },
    }


def load_json_file(path: Path) -> dict[str, object] | None:
    if not path.is_file():
        return None
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None
    return payload if isinstance(payload, dict) else None


def build_latest_index_payload(
    *,
    run_root: Path,
    run_summary: dict[str, object],
    updated_at: str,
) -> dict[str, object]:
    run_status = str(run_summary["status"])
    if run_status == "ok":
        selection_reason = "latest_successful"
    elif run_status == "partial":
        selection_reason = "latest_partial"
    else:
        selection_reason = "latest_active"
    return {
        "schema_version": SCHEMA_VERSION,
        "run_id": run_summary["run_id"],
        "run_root": str(run_root.resolve()),
        "summary_path": str((run_root / "run-summary.json").resolve()),
        "status": run_status,
        "updated_at": updated_at,
        "selection_reason": selection_reason,
    }


def should_update_latest(*, latest_path: Path, current_summary: dict[str, object]) -> bool:
    current_status = str(current_summary["status"])
    existing = load_json_file(latest_path)
    if existing is None:
        return True
    existing_status = str(existing.get("status", "execution_error"))
    if current_status in RUN_SUMMARY_PREFERRED:
        return True
    return existing_status not in RUN_SUMMARY_PREFERRED


def step_reuse_index_path() -> Path:
    return runs_root() / STEP_REUSE_INDEX_FILE_NAME


def load_step_reuse_index() -> dict[str, object]:
    payload = load_json_file(step_reuse_index_path())
    if payload is None:
        return {
            "schema_version": SCHEMA_VERSION,
            "updated_at": None,
            "entries": {},
        }
    raw_entries = payload.get("entries", {})
    normalized_entries: dict[str, dict[str, object]] = {}
    if isinstance(raw_entries, dict):
        for reuse_key, item in raw_entries.items():
            if not isinstance(reuse_key, str) or not reuse_key or not isinstance(item, dict):
                continue
            step_result_path = item.get("step_result_path")
            step_kind = item.get("step_kind")
            run_id = item.get("run_id")
            run_root = item.get("run_root")
            if not all(isinstance(value, str) and value for value in (step_result_path, step_kind, run_id, run_root)):
                continue
            normalized_entries[reuse_key] = {
                "run_id": run_id,
                "run_root": run_root,
                "step_id": item.get("step_id"),
                "step_kind": step_kind,
                "step_result_path": step_result_path,
                "release_tag": item.get("release_tag"),
                "updated_at": item.get("updated_at"),
            }
    return {
        "schema_version": payload.get("schema_version", SCHEMA_VERSION),
        "updated_at": payload.get("updated_at"),
        "entries": normalized_entries,
    }


def save_step_reuse_index(payload: dict[str, object]) -> None:
    write_json(step_reuse_index_path(), payload)


def remove_step_reuse_entry(reuse_key: str) -> None:
    index_path = step_reuse_index_path()
    with filesystem_lock(index_path):
        index = load_step_reuse_index()
        entries = dict(index.get("entries", {})) if isinstance(index.get("entries", {}), dict) else {}
        if reuse_key not in entries:
            return
        entries.pop(reuse_key, None)
        save_step_reuse_index(
            {
                "schema_version": SCHEMA_VERSION,
                "updated_at": datetime.now(timezone.utc).isoformat(),
                "entries": entries,
            }
        )


def prune_invalid_step_reuse_entries() -> int:
    index_path = step_reuse_index_path()
    with filesystem_lock(index_path):
        index = load_step_reuse_index()
        entries = dict(index.get("entries", {})) if isinstance(index.get("entries", {}), dict) else {}
        removed = 0
        for reuse_key, entry in list(entries.items()):
            step_result_path = entry.get("step_result_path")
            if not isinstance(step_result_path, str) or not step_result_path:
                entries.pop(reuse_key, None)
                removed += 1
                continue
            step_result = load_step_result_file(Path(step_result_path))
            if step_result is None or not is_reusable_step(step_result):
                entries.pop(reuse_key, None)
                removed += 1
        if removed:
            save_step_reuse_index(
                {
                    "schema_version": SCHEMA_VERSION,
                    "updated_at": datetime.now(timezone.utc).isoformat(),
                    "entries": entries,
                }
            )
        return removed


def load_step_result_file(path: Path) -> dict[str, object] | None:
    payload = load_json_file(path)
    if payload is None:
        return None
    try:
        return validate_step_result_envelope(payload)
    except ValueError:
        return None


def find_reusable_step_entry(reuse_key: str) -> tuple[dict[str, object], dict[str, object]] | None:
    index = load_step_reuse_index()
    entries = index.get("entries", {})
    if not isinstance(entries, dict):
        return None
    entry = entries.get(reuse_key)
    if not isinstance(entry, dict):
        return None
    step_result_path = entry.get("step_result_path")
    if not isinstance(step_result_path, str) or not step_result_path:
        remove_step_reuse_entry(reuse_key)
        return None
    step_result = load_step_result_file(Path(step_result_path))
    if step_result is None or not is_reusable_step(step_result):
        remove_step_reuse_entry(reuse_key)
        return None
    return entry, step_result


def update_step_reuse_index(*, run_id: str, run_root: Path, release_tag: str | None, step_results: list[dict[str, object]]) -> None:
    index_path = step_reuse_index_path()
    with filesystem_lock(index_path):
        index = load_step_reuse_index()
        entries = dict(index.get("entries", {})) if isinstance(index.get("entries", {}), dict) else {}
        updated_at = datetime.now(timezone.utc).isoformat()
        for step_result in step_results:
            if not is_reusable_step(step_result):
                continue
            reuse_key = step_result.get("reuse_key")
            step_id = step_result.get("step_id")
            step_kind = step_result.get("step_kind")
            step_root = step_result.get("step_root")
            if not all(isinstance(value, str) and value for value in (reuse_key, step_id, step_kind, step_root)):
                continue
            step_result_path = Path(step_root) / "step-result.json"
            if not step_result_path.is_file():
                continue
            entries[reuse_key] = {
                "run_id": run_id,
                "run_root": str(run_root.resolve()),
                "step_id": step_id,
                "step_kind": step_kind,
                "step_result_path": str(step_result_path.resolve()),
                "release_tag": release_tag,
                "updated_at": updated_at,
            }
        save_step_reuse_index(
            {
                "schema_version": SCHEMA_VERSION,
                "updated_at": updated_at,
                "entries": entries,
            }
        )


def build_reuse_logs(
    *,
    step_root: Path,
    payload: dict[str, object],
) -> tuple[list[dict[str, object]], dict[str, object], str]:
    stdout_content = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
    stderr_content = ""
    stdout_path = step_root / "raw.stdout.log"
    stderr_path = step_root / "raw.stderr.log"
    stdout_size = write_text(stdout_path, stdout_content)
    stderr_size = write_text(stderr_path, stderr_content)
    artifacts = [
        {
            "type": "raw_stdout",
            "path": str(stdout_path.resolve()),
            "produced_by_step": step_root.name,
        },
        {
            "type": "raw_stderr",
            "path": str(stderr_path.resolve()),
            "produced_by_step": step_root.name,
        },
    ]
    raw_process = {
        "stdout_path": str(stdout_path.resolve()),
        "stderr_path": str(stderr_path.resolve()),
        "stdout_size": stdout_size,
        "stderr_size": stderr_size,
    }
    return artifacts, raw_process, stdout_content


def materialize_reused_export(
    *,
    step_args: dict[str, object],
    result: dict[str, object],
    artifact_dir: Path,
) -> dict[str, object] | None:
    updated_result = deep_copy_mapping(result)
    suffix = ".java" if str(step_args.get("language")) == "java" else ".smali"
    file_name = str(step_args["class_name"]).replace(".", "_") + suffix
    source_path: Path | None = None
    cache_path = updated_result.get("cache_path")
    if isinstance(cache_path, str) and cache_path:
        candidate = Path(cache_path) / file_name
        if candidate.is_file():
            source_path = candidate.resolve()
    if source_path is None:
        export_path = updated_result.get("export_path")
        if isinstance(export_path, str) and export_path and Path(export_path).is_file():
            source_path = Path(export_path).resolve()
    if source_path is None:
        return None
    target_path = (artifact_dir / file_name).resolve()
    if target_path != source_path:
        target_path.parent.mkdir(parents=True, exist_ok=True)
        target_path.write_bytes(source_path.read_bytes())
    updated_result["export_path"] = str(target_path)
    if "artifactRoot" in updated_result:
        updated_result["artifactRoot"] = str(artifact_dir.resolve())
    if "temporaryPaths" in updated_result:
        updated_result["temporaryPaths"] = []
    scope = updated_result.get("scope")
    if isinstance(scope, dict):
        scope["path"] = str(target_path)
    return updated_result


def build_reused_step_result(
    *,
    task_type: str,
    step: dict[str, object],
    step_root: Path,
    command: list[str],
    reuse_key: str,
    reuse_entry: dict[str, object],
    source_step_result: dict[str, object],
    step_args: dict[str, object],
) -> tuple[dict[str, object], list[dict[str, object]], list[dict[str, object]], list[str]] | None:
    step_kind = str(step["kind"])
    source_result = source_step_result.get("result")
    if not isinstance(source_result, dict):
        return None
    if step_kind == "run_find":
        current_result = deep_copy_mapping(source_result)
        items = current_result.get("items", [])
        evidence_kind = "relation_hit" if task_type in {"trace_callers", "trace_callees"} else "method_hit"
        evidence = build_hit_evidence(
            step_id=str(step["step_id"]),
            items=items if isinstance(items, list) else [],
            kind=evidence_kind,
        )
    elif step_kind == "export_and_scan":
        current_result = materialize_reused_export(
            step_args=step_args,
            result=source_result,
            artifact_dir=step_root / "artifacts",
        )
        if current_result is None:
            return None
        evidence = build_export_evidence(str(step["step_id"]), current_result)
    elif step_kind == "resolve_apk_dex":
        current_result = deep_copy_mapping(source_result)
        evidence = [
            {
                "step_id": str(step["step_id"]),
                "kind": "resolved_dex_candidate",
                "value": candidate_path,
                "source_path": candidate_path,
            }
            for candidate_path in current_result.get("candidate_dex_paths", [])
            if isinstance(candidate_path, str)
        ]
    else:
        return None

    artifacts, raw_process, stdout_content = build_reuse_logs(step_root=step_root, payload=current_result)
    step_result: dict[str, object] = {
        "step_id": str(step["step_id"]),
        "step_kind": step_kind,
        "step_root": str(step_root.resolve()),
        "status": str(source_step_result.get("status", "execution_error")),
        "exit_code": 0,
        "command": command,
        "stdout": stdout_content,
        "stderr": "",
        "raw_process": raw_process,
        "diagnostics": {
            "message": "Step result reused from prior run.",
            "notes": [
                f"reused_from_run={reuse_entry['run_id']}",
                f"reused_from_step={reuse_entry.get('step_id')}",
            ],
        },
        "artifacts": artifacts,
        "result": current_result,
        "reuse_key": reuse_key,
        "reused_from": {
            "run_id": reuse_entry["run_id"],
            "run_root": reuse_entry["run_root"],
            "step_id": reuse_entry.get("step_id"),
            "step_kind": reuse_entry["step_kind"],
            "step_result_path": reuse_entry["step_result_path"],
        },
    }
    if step_kind == "export_and_scan":
        export_path = current_result.get("export_path")
        if isinstance(export_path, str):
            step_result["artifacts"].append(
                {
                    "type": "exported_code",
                    "path": export_path,
                    "produced_by_step": str(step["step_id"]),
                }
            )
    elif step_kind == "resolve_apk_dex":
        resolved_dex_path = current_result.get("resolved_dex_path")
        if isinstance(resolved_dex_path, str):
            step_result["artifacts"].append(
                {
                    "type": "resolved_dex",
                    "path": resolved_dex_path,
                    "produced_by_step": str(step["step_id"]),
                }
            )
    step_result_path = step_root / "step-result.json"
    validate_step_result_envelope(step_result)
    write_json(step_result_path, step_result)
    step_result["artifacts"].append(
        {
            "type": "step_result",
            "path": str(step_result_path.resolve()),
            "produced_by_step": str(step["step_id"]),
        }
    )
    return step_result, [], evidence, []


def persist_run_outputs(
    *,
    run_root: Path,
    run_result: dict[str, object],
    plan: dict[str, object],
    primary_inputs: list[str],
    release_tag: str | None,
    started_at: str,
    finished_at: str,
) -> dict[str, object]:
    updated_at = finished_at
    run_summary_path = run_root / "run-summary.json"
    latest_path = run_root.parent / "latest.json"
    run_summary = build_run_summary_payload(
        run_root=run_root,
        plan=plan,
        run_result=run_result,
        primary_inputs=primary_inputs,
        started_at=started_at,
        finished_at=finished_at,
        updated_at=updated_at,
    )
    validate_run_summary(run_summary)
    write_json(run_summary_path, run_summary)
    if should_update_latest(latest_path=latest_path, current_summary=run_summary):
        latest_index = build_latest_index_payload(
            run_root=run_root,
            run_summary=run_summary,
            updated_at=updated_at,
        )
        validate_latest_index(latest_index)
        write_json(
            latest_path,
            latest_index,
        )
    artifacts = run_result.get("artifacts")
    if isinstance(artifacts, list):
        add_artifact(
            artifacts,
            artifact_type="run_summary",
            path=str(run_summary_path.resolve()),
            produced_by_step="run",
        )
        add_artifact(
            artifacts,
            artifact_type="latest_index",
            path=str(latest_path.resolve()),
            produced_by_step="run",
        )
    step_results = run_result.get("step_results")
    if isinstance(step_results, list):
        update_step_reuse_index(
            run_id=str(run_result.get("run_id", run_root.name)),
            run_root=run_root,
            release_tag=release_tag,
            step_results=step_results,
        )
    final_result_path = run_root / "final_result.json"
    write_json(final_result_path, run_result)
    return run_result


def execute_step(
    *,
    task_type: str,
    step: dict[str, object],
    artifact_root: Path,
    release_tag: str | None,
    anchor: dict[str, object] | None = None,
    prior_step_results: dict[str, dict[str, object]] | None = None,
    cache_refs: dict[str, CachedInputRef] | None = None,
) -> tuple[dict[str, object], list[dict[str, object]], list[dict[str, object]], list[str]]:
    step_id = str(step["step_id"])
    step_root = ensure_step_root(artifact_root, step_id)
    step_args = replace_placeholder(dict(step["args"]), str(artifact_root))
    if not isinstance(step_args, dict):
        raise ValueError("Step args must remain a mapping after placeholder replacement.")
    step_args = resolve_step_arguments(
        step_args,
        prior_step_results=prior_step_results or {},
    )
    step_args, step_cache_keys = prepare_step_args_for_storage(
        step_args,
        step_kind=str(step["kind"]),
        cache_refs={} if cache_refs is None else cache_refs,
    )

    if step["kind"] == "run_find":
        _, internal_limit = determine_limit(task_type, step_args)
        command = build_run_find_command(step_args, limit=internal_limit)
    elif step["kind"] == "export_and_scan":
        command = build_export_and_scan_command(step_args)
    elif step["kind"] == "resolve_apk_dex":
        command = build_resolve_apk_dex_command(step_args)
    else:
        raise ValueError(f"Unsupported step kind: {step['kind']}")

    reuse_key = build_step_reuse_key(
        task_type=task_type,
        release_tag=release_tag,
        step_kind=str(step["kind"]),
        step_args=step_args,
        step_cache_keys=step_cache_keys,
    )
    if reuse_key is not None:
        reusable_entry = find_reusable_step_entry(reuse_key)
        if reusable_entry is not None:
            reuse_entry, source_step_result = reusable_entry
            reused_result = build_reused_step_result(
                task_type=task_type,
                step=step,
                step_root=step_root,
                command=command,
                reuse_key=reuse_key,
                reuse_entry=reuse_entry,
                source_step_result=source_step_result,
                step_args=step_args,
            )
            if reused_result is not None:
                step_result, recommendations, evidence, extra_limits = reused_result
                return step_result, recommendations, evidence, extra_limits

    process_result = run_captured_process(
        step_id=step_id,
        command=command,
        artifact_dir=step_root,
        payload_kind="json",
        extractor=extract_json_payload,
    )
    step_artifacts: list[dict[str, object]] = list(process_result.artifacts)
    recommendations: list[dict[str, object]] = []
    evidence: list[dict[str, object]] = []
    extra_limits: list[str] = []

    step_result: dict[str, object] = {
        "step_id": step_id,
        "step_kind": step["kind"],
        "step_root": str(step_root.resolve()),
        "status": "execution_error" if process_result.exit_code else "empty",
        "exit_code": process_result.exit_code,
        "command": process_result.command,
        "stdout": process_result.stdout,
        "stderr": process_result.stderr,
        "raw_process": process_result.raw_process,
        "diagnostics": dict(process_result.diagnostics),
        "artifacts": step_artifacts,
        "result": {},
    }
    if reuse_key is not None:
        step_result["reuse_key"] = reuse_key

    if process_result.status == "ok":
        try:
            payload = process_result.payload
            if step["kind"] == "run_find":
                status, normalized_result, recommendations, evidence, extra_limits = normalize_run_find_result(
                    task_type=task_type,
                    step_id=step_id,
                    step_args=step_args,
                    anchor=anchor,
                    payload=payload,
                )
            elif step["kind"] == "resolve_apk_dex":
                status, normalized_result, recommendations, evidence, extra_limits = normalize_resolve_apk_dex_result(
                    step_id=step_id,
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
            step_result["stderr"] = (process_result.stderr + f"\nNormalization error: {exc}").strip()
            step_result["diagnostics"] = {
                "message": "Step normalization failed.",
                "cause": str(exc),
            }
    elif process_result.status == "normalization_error":
        step_result["status"] = "execution_error"
        step_result["stderr"] = (
            process_result.stderr + f"\nNormalization error: {process_result.diagnostics.get('cause', '')}"
        ).strip()
        step_result["diagnostics"] = {
            "message": "Step normalization failed.",
            "cause": str(process_result.diagnostics.get("cause", "unknown normalization error")),
        }

    if process_result.status == "ok":
        if step_result["status"] == "ok":
            step_result["diagnostics"] = {"message": "Step completed successfully."}
        elif step_result["status"] == "empty":
            step_result["diagnostics"] = {"message": "Step completed with an empty result."}
        elif step_result["status"] == "ambiguous":
            step_result["diagnostics"] = {"message": "Step completed but result remains ambiguous."}
        elif step_result["status"] == "unsupported":
            step_result["diagnostics"] = {"message": "Step completed but current path is unsupported."}

    if process_result.status != "ok" or step_result["status"] == "execution_error":
        step_result_path = step_root / "step-result.json"
        validate_step_result_envelope(step_result)
        write_json(step_result_path, step_result)
        step_artifacts.append(
            {
                "type": "step_result",
                "path": str(step_result_path.resolve()),
                "produced_by_step": step_id,
            }
        )
        return step_result, recommendations, evidence, extra_limits

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
    elif step["kind"] == "resolve_apk_dex":
        resolved_dex_path = step_result["result"].get("resolved_dex_path")
        if isinstance(resolved_dex_path, str):
            step_artifacts.append(
                {
                    "type": "resolved_dex",
                    "path": resolved_dex_path,
                    "produced_by_step": step_id,
                }
            )
    step_result_path = step_root / "step-result.json"
    validate_step_result_envelope(step_result)
    write_json(step_result_path, step_result)
    step_artifacts.append(
        {
            "type": "step_result",
            "path": str(step_result_path.resolve()),
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
        resolve_step = next((step for step in step_results if step["step_kind"] == "resolve_apk_dex"), None)
        export_step = next((step for step in step_results if step["step_kind"] == "export_and_scan"), None)
        method_anchor = plan["inputs"].get("method_anchor", {})
        exact_anchor = isinstance(method_anchor, dict) and any(
            key in method_anchor for key in ("descriptor", "params", "return_type")
        )
        if resolve_step is not None:
            candidate_dex_paths = resolve_step["result"].get("candidate_dex_paths", [])
            if not isinstance(candidate_dex_paths, list):
                candidate_dex_paths = []
            if resolve_step["status"] == "empty":
                status = "empty"
                summary_text = "Target class was not found in the APK dex set."
                summary_style = "empty"
            elif len(candidate_dex_paths) > 1:
                status = "ambiguous"
                summary_text = "APK resolution matched multiple dex files for the target class."
                summary_style = "ambiguous"
                recommendations.append(
                    {
                        "kind": "narrow_search",
                        "message": "Use a direct dex input if the class is duplicated across extracted APK dex files.",
                        "reason": "apk_resolution_ambiguity",
                    }
                )
                limits.append("APK summarize must resolve to exactly one extracted dex before export")
            elif export_step is None:
                status = "execution_error"
                summary_text = "APK resolution completed, but no export step ran."
                summary_style = "error"
            else:
                step_result = export_step
                export_path = step_result["result"].get("export_path")
                structured_summary = step_result["result"].get("structured_summary", {})
                has_structured_summary = bool(
                    isinstance(structured_summary, dict)
                    and structured_summary.get("supported")
                )
                has_focus_snippets = bool(
                    isinstance(structured_summary, dict)
                    and structured_summary.get("focus_snippet_count")
                )
                large_method_analysis = step_result["result"].get("large_method_analysis", {})
                has_large_method_summary = bool(
                    isinstance(large_method_analysis, dict)
                    and large_method_analysis.get("is_large_method")
                )
                if isinstance(export_path, str) and isinstance(method_anchor, dict):
                    overload_count = count_method_overloads(export_path, str(method_anchor.get("method_name")))
                    if not exact_anchor and overload_count > 1:
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
                        summary_text = (
                            "Resolved one APK dex and summarized one exact method body."
                            if exact_anchor
                            else "Resolved one APK dex and summarized one exported method body."
                        )
                        if has_structured_summary:
                            summary_text += " Included a smali block outline."
                        if has_focus_snippets:
                            summary_text += " Included focused smali snippets."
                        if has_large_method_summary:
                            summary_text += " Attached grouped hotspot compression for the large smali body."
                        summary_style = "partial_support"
                else:
                    status = "execution_error"
                    summary_text = "Export step did not surface an export path."
                    summary_style = "error"
        else:
            step_result = export_step or step_results[0]
            if step_result["status"] == "ok":
                export_path = step_result["result"].get("export_path")
                structured_summary = step_result["result"].get("structured_summary", {})
                has_structured_summary = bool(
                    isinstance(structured_summary, dict)
                    and structured_summary.get("supported")
                )
                has_focus_snippets = bool(
                    isinstance(structured_summary, dict)
                    and structured_summary.get("focus_snippet_count")
                )
                large_method_analysis = step_result["result"].get("large_method_analysis", {})
                has_large_method_summary = bool(
                    isinstance(large_method_analysis, dict)
                    and large_method_analysis.get("is_large_method")
                )
                if isinstance(export_path, str) and isinstance(method_anchor, dict):
                    overload_count = count_method_overloads(export_path, str(method_anchor.get("method_name")))
                    if not exact_anchor and overload_count > 1:
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
                        summary_text = (
                            "Summarized one exact method body."
                            if exact_anchor
                            else "Summarized one exported method body."
                        )
                        if has_structured_summary:
                            summary_text += " Included a smali block outline."
                        if has_focus_snippets:
                            summary_text += " Included focused smali snippets."
                        if has_large_method_summary:
                            summary_text += " Attached grouped hotspot compression for the large smali body."
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
        "cache_root": str(inputs_cache_root().resolve()),
        "temporary_paths": [],
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
    plan_inputs = rewritten_plan.get("inputs", {})
    input_paths = collect_input_paths(plan_inputs.get("input") if isinstance(plan_inputs, dict) else None)
    cache_refs: dict[str, CachedInputRef] = {}
    for input_path in input_paths:
        _, cache_ref = cache_input_path(input_path, ensure_apk_extracted_dex=False)
        register_cache_ref(cache_refs, cache_ref)
    release_tag = resolve_release_tag()
    prune_invalid_step_reuse_entries()
    run_created_at = datetime.now(timezone.utc).isoformat()

    run_meta_path = run_root / "run-meta.json"
    write_json(
        run_meta_path,
        build_run_meta(
            run_id=current_run_id,
            task_type=task_type,
            input_paths=input_paths,
            cache_refs=cache_refs,
            inputs=plan_inputs if isinstance(plan_inputs, dict) else {},
            release_tag=release_tag,
            created_at=run_created_at,
        ),
    )

    step_results: list[dict[str, object]] = []
    recommendations: list[dict[str, object]] = []
    evidence: list[dict[str, object]] = []
    limits = list(rewritten_plan.get("limits", []))
    artifacts: list[dict[str, object]] = [
        {"type": "run_root", "path": str(run_root.resolve()), "produced_by_step": "run"},
        {"type": "run_meta", "path": str(run_meta_path.resolve()), "produced_by_step": "run"},
    ]
    prior_step_results: dict[str, dict[str, object]] = {}

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
            release_tag=release_tag,
            anchor=relation_anchor,
            prior_step_results=prior_step_results,
            cache_refs=cache_refs,
        )
        step_results.append(step_result)
        prior_step_results[str(step_result["step_id"])] = step_result
        recommendations.extend(step_recommendations)
        evidence.extend(step_evidence)
        limits.extend(extra_limits)
        artifacts.extend(step_result["artifacts"])

        if step_result["status"] == "execution_error":
            break
        if step_result["status"] == "empty" and not bool(step.get("allow_empty_result", False)):
            break
        if (
            step_result["step_kind"] == "resolve_apk_dex"
            and not step_result.get("result", {}).get("resolved_dex_path")
        ):
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
    write_json(
        run_meta_path,
        build_run_meta(
            run_id=current_run_id,
            task_type=task_type,
            input_paths=input_paths,
            cache_refs=cache_refs,
            inputs=plan_inputs if isinstance(plan_inputs, dict) else {},
            release_tag=release_tag,
            created_at=run_created_at,
        ),
    )
    return persist_run_outputs(
        run_root=run_root,
        run_result=final_result,
        plan=rewritten_plan,
        primary_inputs=input_paths,
        release_tag=release_tag,
        started_at=run_created_at,
        finished_at=datetime.now(timezone.utc).isoformat(),
    )

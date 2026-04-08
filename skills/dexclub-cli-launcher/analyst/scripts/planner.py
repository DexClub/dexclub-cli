#!/usr/bin/env python3
from __future__ import annotations

from dataclasses import dataclass
import os
from pathlib import Path
from typing import Any

from method_descriptor import build_full_method_descriptor, parse_method_descriptor
from plan_schema import (
    PLANNER_VERSION,
    RUN_ARTIFACT_ROOT_PLACEHOLDER,
    SCHEMA_VERSION,
    TASK_REGISTRY,
    get_task_definition,
    join_artifact_path,
)
from query_builder import build_relation_query


@dataclass(frozen=True, slots=True)
class PlannerError(Exception):
    status: str
    message: str
    details: dict[str, object] | None = None

    def __str__(self) -> str:
        return self.message


def build_plan_error_payload(task_type: str, error: PlannerError) -> dict[str, object]:
    payload: dict[str, object] = {
        "schema_version": SCHEMA_VERSION,
        "planner_version": PLANNER_VERSION,
        "task_type": task_type,
        "status": error.status,
        "message": error.message,
    }
    if error.details:
        payload["details"] = error.details
    return payload


def ensure_mapping(value: object, *, field_name: str) -> dict[str, object]:
    if not isinstance(value, dict):
        raise PlannerError("input_error", f"`{field_name}` must be a JSON object.")
    return dict(value)


def normalize_paths(value: object) -> tuple[object, list[str]]:
    if isinstance(value, str):
        raw_input: object = value
        path_values = [value]
    elif isinstance(value, list) and value and all(isinstance(item, str) for item in value):
        raw_input = list(value)
        path_values = list(value)
    else:
        raise PlannerError("input_error", "`input` must be a non-empty path string or path array.")

    resolved_paths: list[str] = []
    for raw_path in path_values:
        path_obj = Path(raw_path).expanduser()
        if not path_obj.exists():
            raise PlannerError("input_error", f"Input path not found: {path_obj}")
        if not path_obj.is_file():
            raise PlannerError("input_error", f"Input path is not a file: {path_obj.resolve()}")
        if not os.access(path_obj, os.R_OK):
            raise PlannerError("input_error", f"Input path is not readable: {path_obj.resolve()}")
        resolved_paths.append(str(path_obj.resolve()))
    return raw_input, resolved_paths


def detect_primary_kind(paths: list[str]) -> str:
    suffixes = {Path(path).suffix.lower() for path in paths}
    supported_suffixes = {".apk", ".dex", ".java", ".smali"}
    unsupported_suffixes = sorted(suffix for suffix in suffixes if suffix not in supported_suffixes)
    if unsupported_suffixes:
        raise PlannerError(
            "input_error",
            "Unsupported input extension in version 1.",
            {"extensions": unsupported_suffixes},
        )
    if suffixes == {".apk"}:
        if len(paths) != 1:
            raise PlannerError("unsupported", "Version 1 does not support multiple APK inputs.")
        return "apk"
    if suffixes == {".dex"}:
        return "dex" if len(paths) == 1 else "dex_set"
    if suffixes <= {".java", ".smali"}:
        if len(paths) != 1:
            raise PlannerError("unsupported", "Version 1 does not support multiple exported-code inputs.")
        return "exported_code"
    if ".apk" in suffixes and ".dex" in suffixes:
        raise PlannerError("unsupported", "Mixed APK and dex inputs are invalid in version 1.")
    raise PlannerError(
        "input_error",
        "Mixed input kinds are not supported in version 1.",
        {"suffixes": sorted(suffixes)},
    )


def normalize_inputs(raw_input: object) -> tuple[object, dict[str, object]]:
    preserved_input, paths = normalize_paths(raw_input)
    primary_kind = detect_primary_kind(paths)
    return preserved_input, {
        "primary_kind": primary_kind,
        "paths": paths,
        "path_count": len(paths),
    }


def normalize_search_packages(value: object | None) -> list[str]:
    if value is None:
        return []
    if isinstance(value, str):
        packages = [value]
    elif isinstance(value, list) and all(isinstance(item, str) for item in value):
        packages = list(value)
    else:
        raise PlannerError("input_error", "`search_package` must be a string or string array.")
    normalized = [item.strip() for item in packages if item.strip()]
    return normalized


def normalize_limit(value: object | None) -> int | None:
    if value is None:
        return None
    if not isinstance(value, int):
        raise PlannerError("input_error", "`limit` must be an integer.")
    if value <= 0:
        raise PlannerError("input_error", "`limit` must be greater than zero.")
    return value


def normalize_method_anchor(value: object) -> dict[str, object]:
    if isinstance(value, str):
        declared_class, separator, method_name = value.partition("#")
        if not separator or not declared_class or not method_name:
            raise PlannerError(
                "input_error",
                "Compact `method_anchor` must use `ClassName#methodName` form.",
            )
        return {
            "class_name": declared_class,
            "method_name": method_name,
            "compact": value,
            "is_relaxed": True,
        }

    anchor = ensure_mapping(value, field_name="method_anchor")
    class_name = anchor.get("class_name")
    method_name = anchor.get("method_name")
    if not isinstance(class_name, str) or not class_name.strip():
        raise PlannerError("input_error", "`method_anchor.class_name` is required.")
    if not isinstance(method_name, str) or not method_name.strip():
        raise PlannerError("input_error", "`method_anchor.method_name` is required.")

    normalized: dict[str, object] = {
        "class_name": class_name.strip(),
        "method_name": method_name.strip(),
        "compact": f"{class_name.strip()}#{method_name.strip()}",
        "is_relaxed": True,
    }
    for key in ("descriptor", "return_type"):
        anchor_value = anchor.get(key)
        if anchor_value is None:
            continue
        if not isinstance(anchor_value, str) or not anchor_value.strip():
            raise PlannerError("input_error", f"`method_anchor.{key}` must be a non-empty string.")
        normalized[key] = anchor_value.strip()
    params = anchor.get("params")
    if params is not None:
        if not isinstance(params, list) or not all(isinstance(item, str) and item.strip() for item in params):
            raise PlannerError("input_error", "`method_anchor.params` must be a non-empty string array.")
        normalized["params"] = [item.strip() for item in params]
    if "descriptor" in normalized:
        try:
            parsed = parse_method_descriptor(
                str(normalized["descriptor"]),
                class_name=str(normalized["class_name"]),
                method_name=str(normalized["method_name"]),
            )
        except ValueError as exc:
            raise PlannerError("input_error", str(exc)) from exc
        normalized["class_name"] = parsed.class_name
        normalized["method_name"] = parsed.method_name
        normalized["descriptor"] = parsed.descriptor
        if "params" in normalized and list(parsed.params) != normalized["params"]:
            raise PlannerError("input_error", "`method_anchor.params` does not match `method_anchor.descriptor`.")
        if "return_type" in normalized and str(normalized["return_type"]) != parsed.return_type:
            raise PlannerError("input_error", "`method_anchor.return_type` does not match `method_anchor.descriptor`.")
        normalized["params"] = list(parsed.params)
        normalized["return_type"] = parsed.return_type
    elif "params" in normalized and "return_type" in normalized:
        try:
            normalized["descriptor"] = build_full_method_descriptor(
                class_name=str(normalized["class_name"]),
                method_name=str(normalized["method_name"]),
                params=list(normalized["params"]),
                return_type=str(normalized["return_type"]),
            )
        except ValueError as exc:
            raise PlannerError("input_error", str(exc)) from exc
    if any(key in normalized for key in ("descriptor", "params", "return_type")):
        normalized["is_relaxed"] = False
    return normalized


def ensure_required_fields(task_type: str, inputs: dict[str, object]) -> None:
    task_definition = TASK_REGISTRY[task_type]
    for field_name in task_definition.required_args:
        if field_name not in inputs:
            raise PlannerError("input_error", f"Missing required field: `{field_name}`.")


def build_common_limits(task_type: str, method_anchor: dict[str, object] | None = None) -> list[str]:
    task_definition = TASK_REGISTRY[task_type]
    limits = list(task_definition.current_limits)
    if method_anchor is not None and bool(method_anchor.get("is_relaxed", True)):
        limits.append("method anchor does not include a descriptor; overloads may be ambiguous")
    if (
        task_type in {"trace_callers", "trace_callees"}
        and method_anchor is not None
        and bool(method_anchor.get("is_relaxed", True))
    ):
        limits.append("current version cannot verify overload uniqueness for relaxed relation anchors")
    return limits


def build_summarize_limits(
    *,
    normalized_inputs: dict[str, object],
    method_anchor: dict[str, object],
) -> list[str]:
    limits = build_common_limits("summarize_method_logic", method_anchor)
    if normalized_inputs["primary_kind"] == "apk":
        limits.append("APK summarize resolves the target class to one extracted dex before export")
    return limits


def build_expected_outputs(task_type: str) -> list[str]:
    task_definition = TASK_REGISTRY[task_type]
    outputs = ["structured step result", "human-readable summary"]
    if task_definition.result_shape == "common_hit_list":
        outputs.insert(1, "normalized hit list")
    elif task_definition.result_shape == "relation":
        outputs.insert(1, "normalized relation hit list")
    elif task_definition.result_shape == "export_and_scan":
        outputs.insert(1, "exported code artifact")
    return outputs


def build_run_find_step(
    *,
    task_type: str,
    normalized_inputs: dict[str, object],
    limit: int | None,
    search_package: list[str],
    declared_class: str | None = None,
    using_string: str | None = None,
    using_number: str | None = None,
    method_anchor: dict[str, object] | None = None,
) -> dict[str, object]:
    args: dict[str, object] = {
        "kind": "method",
        "inputs": list(normalized_inputs["paths"]),
        "output_format": "json",
    }
    if limit is not None:
        args["limit"] = limit
    if search_package:
        args["search_package"] = search_package
    if declared_class:
        args["declared_class"] = declared_class
    if using_string is not None:
        args["using_string"] = [using_string]
    if using_number is not None:
        args["using_number"] = [using_number]
    if method_anchor is not None:
        if bool(method_anchor.get("is_relaxed", True)):
            if task_type == "trace_callers":
                args["invoke_method"] = [str(method_anchor["compact"])]
            elif task_type == "trace_callees":
                args["caller_method"] = [str(method_anchor["compact"])]
        else:
            relation_direction = "callers" if task_type == "trace_callers" else "callees"
            args["raw_query_json"] = build_relation_query(
                relation_direction=relation_direction,
                anchor=method_anchor,
                search_packages=search_package,
            )

    return {
        "step_id": "step-1",
        "kind": "run_find",
        "tool": "run_find.py",
        "args": args,
        "allow_empty_result": True,
    }


def build_export_and_scan_step(
    *,
    normalized_inputs: dict[str, object],
    method_anchor: dict[str, object],
    artifact_root: str,
    language: str | None,
    mode: str | None,
) -> dict[str, object]:
    step_args: dict[str, object] = {
        "input_dex": normalized_inputs["paths"][0],
        "class_name": method_anchor["class_name"],
        "method": method_anchor["method_name"],
        "language": language or "smali",
        "mode": mode or "summary",
        "format": "json",
        "output_dir": join_artifact_path(artifact_root, "exports"),
    }
    if method_anchor.get("descriptor"):
        step_args["method_descriptor"] = method_anchor["descriptor"]
    return {
        "step_id": "step-1",
        "kind": "export_and_scan",
        "tool": "export_and_scan.py",
        "args": step_args,
        "allow_empty_result": False,
    }


def build_resolve_apk_dex_step(
    *,
    normalized_inputs: dict[str, object],
    method_anchor: dict[str, object],
    artifact_root: str,
) -> dict[str, object]:
    return {
        "step_id": "step-1",
        "kind": "resolve_apk_dex",
        "tool": "resolve_apk_dex.py",
        "args": {
            "input_apk": normalized_inputs["paths"][0],
            "class_name": method_anchor["class_name"],
            "format": "json",
            "output_dir": join_artifact_path(artifact_root, "resolved", "step-1"),
        },
        "allow_empty_result": False,
    }


def build_plan(
    *,
    task_type: str,
    inputs: dict[str, object],
    artifact_root: str | None = None,
) -> dict[str, object]:
    task_definition = get_task_definition(task_type)
    if task_definition is None:
        raise PlannerError("unsupported", f"Unsupported task type: `{task_type}`.")

    normalized_external_inputs = ensure_mapping(inputs, field_name="input_json")
    ensure_required_fields(task_type, normalized_external_inputs)

    preserved_input, normalized_inputs = normalize_inputs(normalized_external_inputs["input"])
    if normalized_inputs["primary_kind"] not in task_definition.accepted_input_kinds:
        raise PlannerError(
            "unsupported",
            f"`{task_type}` does not accept `{normalized_inputs['primary_kind']}` input in version 1.",
        )

    plan_inputs: dict[str, object] = {"input": preserved_input}
    steps: list[dict[str, object]]
    method_anchor: dict[str, object] | None = None

    if task_type == "search_methods_by_string":
        value = normalized_external_inputs.get("string")
        if not isinstance(value, str) or not value:
            raise PlannerError("input_error", "`string` must be a non-empty string.")
        declared_class = normalized_external_inputs.get("declared_class")
        if declared_class is not None and (not isinstance(declared_class, str) or not declared_class.strip()):
            raise PlannerError("input_error", "`declared_class` must be a non-empty string.")
        search_package = normalize_search_packages(normalized_external_inputs.get("search_package"))
        limit = normalize_limit(normalized_external_inputs.get("limit"))
        plan_inputs["string"] = value
        if declared_class:
            plan_inputs["declared_class"] = declared_class.strip()
        if search_package:
            plan_inputs["search_package"] = search_package
        if limit is not None:
            plan_inputs["limit"] = limit
        steps = [
            build_run_find_step(
                task_type=task_type,
                normalized_inputs=normalized_inputs,
                limit=limit,
                search_package=search_package,
                declared_class=declared_class.strip() if isinstance(declared_class, str) else None,
                using_string=value,
            )
        ]
    elif task_type == "search_methods_by_number":
        raw_number = normalized_external_inputs.get("number")
        if not isinstance(raw_number, (int, float, str)):
            raise PlannerError("input_error", "`number` must be a number or numeric string.")
        declared_class = normalized_external_inputs.get("declared_class")
        if declared_class is not None and (not isinstance(declared_class, str) or not declared_class.strip()):
            raise PlannerError("input_error", "`declared_class` must be a non-empty string.")
        search_package = normalize_search_packages(normalized_external_inputs.get("search_package"))
        limit = normalize_limit(normalized_external_inputs.get("limit"))
        number_literal = str(raw_number)
        plan_inputs["number"] = raw_number
        if declared_class:
            plan_inputs["declared_class"] = declared_class.strip()
        if search_package:
            plan_inputs["search_package"] = search_package
        if limit is not None:
            plan_inputs["limit"] = limit
        steps = [
            build_run_find_step(
                task_type=task_type,
                normalized_inputs=normalized_inputs,
                limit=limit,
                search_package=search_package,
                declared_class=declared_class.strip() if isinstance(declared_class, str) else None,
                using_number=number_literal,
            )
        ]
    elif task_type in {"trace_callers", "trace_callees"}:
        method_anchor = normalize_method_anchor(normalized_external_inputs["method_anchor"])
        search_package = normalize_search_packages(normalized_external_inputs.get("search_package"))
        limit = normalize_limit(normalized_external_inputs.get("limit"))
        plan_inputs["method_anchor"] = {
            key: value
            for key, value in method_anchor.items()
            if key in {"class_name", "method_name", "descriptor", "params", "return_type"}
        }
        if search_package:
            plan_inputs["search_package"] = search_package
        if limit is not None:
            plan_inputs["limit"] = limit
        steps = [
            build_run_find_step(
                task_type=task_type,
                normalized_inputs=normalized_inputs,
                limit=limit,
                search_package=search_package,
                method_anchor=method_anchor,
            )
        ]
    elif task_type == "summarize_method_logic":
        if int(normalized_inputs["path_count"]) != 1:
            raise PlannerError("unsupported", "`summarize_method_logic` requires exactly one APK or dex input.")
        method_anchor = normalize_method_anchor(normalized_external_inputs["method_anchor"])
        if not bool(method_anchor.get("is_relaxed", True)) and "descriptor" not in method_anchor:
            raise PlannerError(
                "unsupported",
                "Descriptor-aware summarize requires either `method_anchor.descriptor` or both `params` and `return_type`.",
            )
        language = normalized_external_inputs.get("language")
        if language is not None and language not in {"java", "smali"}:
            raise PlannerError("input_error", "`language` must be `java` or `smali`.")
        mode = normalized_external_inputs.get("mode")
        if mode is not None and mode not in {"summary", "strings", "numbers", "calls", "fields", "all"}:
            raise PlannerError("input_error", "`mode` is invalid for `summarize_method_logic`.")
        plan_inputs["method_anchor"] = {
            key: value
            for key, value in method_anchor.items()
            if key in {"class_name", "method_name", "descriptor", "params", "return_type"}
        }
        if language is not None:
            plan_inputs["language"] = language
        if mode is not None:
            plan_inputs["mode"] = mode
        if normalized_inputs["primary_kind"] == "apk":
            steps = [
                build_resolve_apk_dex_step(
                    normalized_inputs=normalized_inputs,
                    method_anchor=method_anchor,
                    artifact_root=artifact_root or RUN_ARTIFACT_ROOT_PLACEHOLDER,
                ),
                {
                    "step_id": "step-2",
                    "kind": "export_and_scan",
                    "tool": "export_and_scan.py",
                    "args": {
                        "input_dex_from_step": "step-1",
                        "class_name": method_anchor["class_name"],
                        "method": method_anchor["method_name"],
                        "language": language if isinstance(language, str) else task_definition.default_language,
                        "mode": mode if isinstance(mode, str) else task_definition.default_mode,
                        "format": "json",
                        "output_dir": join_artifact_path(
                            artifact_root or RUN_ARTIFACT_ROOT_PLACEHOLDER,
                            "exports",
                        ),
                    },
                    "allow_empty_result": False,
                },
            ]
            if method_anchor.get("descriptor"):
                steps[1]["args"]["method_descriptor"] = method_anchor["descriptor"]
        elif normalized_inputs["primary_kind"] == "dex":
            steps = [
                build_export_and_scan_step(
                    normalized_inputs=normalized_inputs,
                    method_anchor=method_anchor,
                    artifact_root=artifact_root or RUN_ARTIFACT_ROOT_PLACEHOLDER,
                    language=language if isinstance(language, str) else task_definition.default_language,
                    mode=mode if isinstance(mode, str) else task_definition.default_mode,
                )
            ]
        else:
            raise PlannerError("unsupported", "`summarize_method_logic` accepts one APK or one dex input in this version.")
    else:
        raise PlannerError("unsupported", f"Unsupported task type: `{task_type}`.")

    return {
        "schema_version": SCHEMA_VERSION,
        "planner_version": PLANNER_VERSION,
        "task_type": task_type,
        "inputs": plan_inputs,
        "normalized_inputs": normalized_inputs,
        "steps": steps,
        "limits": (
            build_summarize_limits(normalized_inputs=normalized_inputs, method_anchor=method_anchor)
            if task_type == "summarize_method_logic" and method_anchor is not None
            else build_common_limits(task_type, method_anchor)
        ),
        "expected_outputs": build_expected_outputs(task_type),
        "stop_conditions": [
            "stop after the planned steps complete",
            "do not expand into additional hops automatically",
        ],
    }

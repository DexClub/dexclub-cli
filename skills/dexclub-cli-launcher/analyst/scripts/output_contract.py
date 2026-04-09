#!/usr/bin/env python3
from __future__ import annotations


RUN_SUMMARY_STATUSES = {"ok", "partial", "input_error", "execution_error", "cancelled"}
STEP_STATUSES = {
    "ok",
    "empty",
    "ambiguous",
    "unsupported",
    "input_error",
    "execution_error",
    "normalization_error",
}
LATEST_SELECTION_REASONS = {"latest_active", "latest_successful", "latest_partial"}
SUMMARY_STYLES = {"ok", "warning", "error"}


def _require_mapping(value: object, *, field_name: str) -> dict[str, object]:
    if not isinstance(value, dict):
        raise ValueError(f"`{field_name}` must be an object.")
    return value


def _require_non_empty_string(value: object, *, field_name: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"`{field_name}` must be a non-empty string.")
    return value


def _require_bool(value: object, *, field_name: str) -> bool:
    if not isinstance(value, bool):
        raise ValueError(f"`{field_name}` must be a boolean.")
    return value


def _require_int(value: object, *, field_name: str) -> int:
    if not isinstance(value, int):
        raise ValueError(f"`{field_name}` must be an integer.")
    return value


def _require_string_list(value: object, *, field_name: str) -> list[str]:
    if not isinstance(value, list):
        raise ValueError(f"`{field_name}` must be a string array.")
    result: list[str] = []
    for index, item in enumerate(value):
        result.append(_require_non_empty_string(item, field_name=f"{field_name}[{index}]"))
    return result


def _require_enum(value: object, *, field_name: str, allowed: set[str]) -> str:
    string_value = _require_non_empty_string(value, field_name=field_name)
    if string_value not in allowed:
        allowed_text = ", ".join(sorted(allowed))
        raise ValueError(f"`{field_name}` must be one of: {allowed_text}.")
    return string_value


def validate_key_artifact_item(item: object) -> dict[str, object]:
    payload = _require_mapping(item, field_name="key_artifact")
    _require_non_empty_string(payload.get("type"), field_name="key_artifact.type")
    _require_non_empty_string(payload.get("path"), field_name="key_artifact.path")
    _require_non_empty_string(payload.get("label"), field_name="key_artifact.label")
    _require_non_empty_string(payload.get("reason"), field_name="key_artifact.reason")
    return payload


def validate_step_index_item(item: object) -> dict[str, object]:
    payload = _require_mapping(item, field_name="step_index_item")
    _require_non_empty_string(payload.get("step_id"), field_name="step_index_item.step_id")
    _require_non_empty_string(payload.get("step_kind"), field_name="step_index_item.step_kind")
    _require_enum(payload.get("status"), field_name="step_index_item.status", allowed=STEP_STATUSES)
    _require_non_empty_string(payload.get("step_root"), field_name="step_index_item.step_root")
    _require_int(payload.get("attempt_count"), field_name="step_index_item.attempt_count")
    _require_bool(payload.get("is_reusable_step"), field_name="step_index_item.is_reusable_step")
    _require_bool(payload.get("is_required"), field_name="step_index_item.is_required")
    return payload


def validate_run_summary(summary: object) -> dict[str, object]:
    payload = _require_mapping(summary, field_name="run_summary")
    for field_name in (
        "schema_version",
        "run_id",
        "run_root",
        "task_type",
        "input_source",
        "started_at",
        "finished_at",
        "updated_at",
    ):
        _require_non_empty_string(payload.get(field_name), field_name=f"run_summary.{field_name}")
    _require_enum(payload.get("status"), field_name="run_summary.status", allowed=RUN_SUMMARY_STATUSES)
    latest_step_id = payload.get("latest_step_id")
    if latest_step_id is not None:
        _require_non_empty_string(latest_step_id, field_name="run_summary.latest_step_id")
    latest_successful_step_id = payload.get("latest_successful_step_id")
    if latest_successful_step_id is not None:
        _require_non_empty_string(
            latest_successful_step_id,
            field_name="run_summary.latest_successful_step_id",
        )
    _require_string_list(payload.get("primary_inputs"), field_name="run_summary.primary_inputs")
    step_index = payload.get("step_index")
    if not isinstance(step_index, list):
        raise ValueError("`run_summary.step_index` must be a list.")
    for item in step_index:
        validate_step_index_item(item)
    key_artifacts = payload.get("key_artifacts")
    if not isinstance(key_artifacts, list):
        raise ValueError("`run_summary.key_artifacts` must be a list.")
    for item in key_artifacts:
        validate_key_artifact_item(item)
    summary_payload = _require_mapping(payload.get("summary"), field_name="run_summary.summary")
    _require_non_empty_string(summary_payload.get("text"), field_name="run_summary.summary.text")
    _require_enum(summary_payload.get("style"), field_name="run_summary.summary.style", allowed=SUMMARY_STYLES)
    return payload


def validate_latest_index(index: object) -> dict[str, object]:
    payload = _require_mapping(index, field_name="latest_index")
    for field_name in ("schema_version", "run_id", "run_root", "summary_path", "updated_at"):
        _require_non_empty_string(payload.get(field_name), field_name=f"latest_index.{field_name}")
    _require_enum(payload.get("status"), field_name="latest_index.status", allowed=RUN_SUMMARY_STATUSES)
    _require_enum(
        payload.get("selection_reason"),
        field_name="latest_index.selection_reason",
        allowed=LATEST_SELECTION_REASONS,
    )
    return payload

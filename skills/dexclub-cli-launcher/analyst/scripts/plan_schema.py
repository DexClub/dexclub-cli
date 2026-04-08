#!/usr/bin/env python3
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

SCHEMA_VERSION = "1"
PLANNER_VERSION = "1"
RUN_ARTIFACT_ROOT_PLACEHOLDER = "__RUN_ARTIFACT_ROOT__"
DEFAULT_MAX_DIRECT_HITS = 20

SUPPORTED_PRIMARY_KINDS = (
    "apk",
    "dex",
    "dex_set",
    "exported_code",
)


@dataclass(frozen=True, slots=True)
class TaskDefinition:
    task_type: str
    required_args: tuple[str, ...]
    optional_args: tuple[str, ...]
    accepted_input_kinds: tuple[str, ...]
    step_kind: str
    result_shape: str
    current_limits: tuple[str, ...]
    max_direct_hits: int | None = DEFAULT_MAX_DIRECT_HITS
    relation_direction: str | None = None
    default_language: str | None = None
    default_mode: str | None = None


TASK_REGISTRY: dict[str, TaskDefinition] = {
    "search_methods_by_string": TaskDefinition(
        task_type="search_methods_by_string",
        required_args=("input", "string"),
        optional_args=("declared_class", "search_package", "limit"),
        accepted_input_kinds=("apk", "dex", "dex_set"),
        step_kind="run_find",
        result_shape="common_hit_list",
        current_limits=(
            "only direct query matching",
            "no automatic result expansion after threshold overflow",
        ),
    ),
    "search_methods_by_number": TaskDefinition(
        task_type="search_methods_by_number",
        required_args=("input", "number"),
        optional_args=("declared_class", "search_package", "limit"),
        accepted_input_kinds=("apk", "dex", "dex_set"),
        step_kind="run_find",
        result_shape="common_hit_list",
        current_limits=(
            "only direct query matching",
            "no automatic result expansion after threshold overflow",
        ),
    ),
    "summarize_method_logic": TaskDefinition(
        task_type="summarize_method_logic",
        required_args=("input", "method_anchor"),
        optional_args=("language", "mode"),
        accepted_input_kinds=("apk", "dex"),
        step_kind="export_and_scan",
        result_shape="export_and_scan",
        current_limits=(
            "only direct method body analysis",
            "uses one exact resolved dex as the export input",
            "no automatic resource analysis",
        ),
        max_direct_hits=None,
        default_language="smali",
        default_mode="summary",
    ),
    "trace_callers": TaskDefinition(
        task_type="trace_callers",
        required_args=("input", "method_anchor"),
        optional_args=("search_package", "limit"),
        accepted_input_kinds=("apk", "dex", "dex_set"),
        step_kind="run_find",
        result_shape="relation",
        current_limits=(
            "only direct caller matching",
            "not a full call graph expansion",
        ),
        relation_direction="callers",
    ),
    "trace_callees": TaskDefinition(
        task_type="trace_callees",
        required_args=("input", "method_anchor"),
        optional_args=("search_package", "limit"),
        accepted_input_kinds=("apk", "dex", "dex_set"),
        step_kind="run_find",
        result_shape="relation",
        current_limits=(
            "only direct callee matching",
            "not a full call graph expansion",
        ),
        relation_direction="callees",
    ),
}


def get_task_definition(task_type: str) -> TaskDefinition | None:
    return TASK_REGISTRY.get(task_type)


def join_artifact_path(root: str, *parts: str) -> str:
    if root == RUN_ARTIFACT_ROOT_PLACEHOLDER:
        return "/".join([root.rstrip("/"), *parts])
    return str(Path(root, *parts).resolve())

#!/usr/bin/env python3
from __future__ import annotations

from typing import Iterable


SECTION_NAMES = ("verifiedFacts", "inferences", "unknowns", "nextChecks")
MAX_EVIDENCE_ITEMS = 3


def empty_analysis_sections() -> dict[str, list[dict[str, object]]]:
    return {name: [] for name in SECTION_NAMES}


def build_analysis_sections(
    *,
    task_type: str,
    status: str,
    summary_text: str,
    plan: dict[str, object] | None = None,
    step_results: list[dict[str, object]] | None = None,
    evidence: list[dict[str, object]] | None = None,
    recommendations: list[dict[str, object]] | None = None,
    limits: list[str] | None = None,
) -> dict[str, list[dict[str, object]]]:
    sections = empty_analysis_sections()
    normalized_plan = plan if isinstance(plan, dict) else {}
    normalized_step_results = step_results if isinstance(step_results, list) else []
    normalized_evidence = evidence if isinstance(evidence, list) else []
    normalized_recommendations = recommendations if isinstance(recommendations, list) else []
    normalized_limits = limits if isinstance(limits, list) else []

    summary_evidence = _pick_evidence(normalized_evidence)
    if summary_text:
        _append_item(sections["verifiedFacts"], text=summary_text, evidence=summary_evidence)

    if not normalized_step_results:
        if status in {"input_error", "unsupported"}:
            _append_item(
                sections["verifiedFacts"],
                text="The planner stopped before any execution step started.",
                reason=status,
            )
        elif status == "execution_error":
            _append_item(
                sections["verifiedFacts"],
                text="The run failed before producing any executable step result.",
                reason="no_step_results",
            )
    else:
        failed_step = _find_first_step_with_status(normalized_step_results, "execution_error")
        if failed_step is not None:
            _append_item(
                sections["verifiedFacts"],
                text=f"Step `{failed_step.get('step_id')}` is the first recorded execution failure.",
                reason="execution_error",
            )

    if task_type == "summarize_method_logic":
        _populate_summarize_sections(
            sections=sections,
            status=status,
            plan=normalized_plan,
            step_results=normalized_step_results,
            evidence=normalized_evidence,
        )
    else:
        _populate_search_sections(
            sections=sections,
            task_type=task_type,
            status=status,
            plan=normalized_plan,
            step_results=normalized_step_results,
            evidence=normalized_evidence,
        )

    _apply_limit_sections(
        sections=sections,
        task_type=task_type,
        limits=normalized_limits,
    )
    _apply_recommendation_sections(
        sections=sections,
        recommendations=normalized_recommendations,
    )
    _apply_generic_status_sections(
        sections=sections,
        task_type=task_type,
        status=status,
        step_results=normalized_step_results,
    )
    return sections


def _populate_summarize_sections(
    *,
    sections: dict[str, list[dict[str, object]]],
    status: str,
    plan: dict[str, object],
    step_results: list[dict[str, object]],
    evidence: list[dict[str, object]],
) -> None:
    inputs = plan.get("inputs") if isinstance(plan.get("inputs"), dict) else {}
    method_anchor = inputs.get("method_anchor") if isinstance(inputs.get("method_anchor"), dict) else {}
    exact_anchor = any(key in method_anchor for key in ("descriptor", "params", "return_type"))
    language = inputs.get("language") if isinstance(inputs.get("language"), str) else "smali"

    if method_anchor:
        anchor_mode = "descriptor-aware" if exact_anchor else "relaxed"
        _append_item(
            sections["verifiedFacts"],
            text=f"The summarize anchor is using a {anchor_mode} method selector.",
            reason="method_anchor",
        )

    resolve_step = _find_first_step(step_results, "resolve_apk_dex")
    export_step = _find_first_step(step_results, "export_and_scan")
    resolve_result = resolve_step.get("result") if isinstance(resolve_step, dict) and isinstance(resolve_step.get("result"), dict) else {}
    export_result = export_step.get("result") if isinstance(export_step, dict) and isinstance(export_step.get("result"), dict) else {}

    resolved_dex_path = resolve_result.get("resolved_dex_path")
    if isinstance(resolved_dex_path, str) and resolved_dex_path:
        _append_item(
            sections["verifiedFacts"],
            text="The target class was resolved to one concrete dex file before export.",
            evidence=_pick_evidence(evidence, kind="resolved_dex_candidate", source_path=resolved_dex_path),
        )

    candidate_dex_paths = resolve_result.get("candidate_dex_paths")
    if isinstance(candidate_dex_paths, list) and len(candidate_dex_paths) > 1:
        dex_evidence = _pick_evidence(evidence, kind="resolved_dex_candidate")
        _append_item(
            sections["verifiedFacts"],
            text=f"APK resolution produced {len(candidate_dex_paths)} candidate dex files for the target class.",
            evidence=dex_evidence,
        )
        _append_item(
            sections["unknowns"],
            text="The exact dex that should be exported is still unresolved.",
            reason="multiple_candidate_dex",
        )

    export_path = export_result.get("export_path")
    export_evidence = _pick_evidence(evidence, source_path=export_path if isinstance(export_path, str) else None)
    if isinstance(export_path, str) and export_path:
        _append_item(
            sections["verifiedFacts"],
            text="The run produced an exported code artifact for the target class.",
            evidence=export_evidence,
        )

    structured_summary = export_result.get("structured_summary")
    if not isinstance(structured_summary, dict):
        structured_summary = {}
    if structured_summary.get("supported") is True:
        _append_item(
            sections["verifiedFacts"],
            text="The result includes a structured_summary block.",
            evidence=export_evidence,
        )
        _append_item(
            sections["inferences"],
            text="The exported method already has enough structure to continue review from grouped blocks instead of only raw code.",
            confidence="high",
            evidence=export_evidence,
        )
    elif language == "java" and export_step is not None:
        _append_item(
            sections["verifiedFacts"],
            text="The current Java summarize path still reports structured_summary as unsupported.",
            evidence=export_evidence,
        )
        _append_item(
            sections["unknowns"],
            text="Block-level structured grouping is still unavailable on the Java summarize path.",
            reason="java_structured_summary_unsupported",
        )
        _append_item(
            sections["nextChecks"],
            text="Rerun the same summarize task with `language=smali` if block grouping is required.",
            reason="switch_to_smali",
        )

    focus_snippet_count = structured_summary.get("focus_snippet_count")
    if isinstance(focus_snippet_count, int) and focus_snippet_count > 0:
        _append_item(
            sections["verifiedFacts"],
            text=f"The structured summary exposed {focus_snippet_count} focused snippet(s).",
            evidence=export_evidence,
        )
        _append_item(
            sections["nextChecks"],
            text="Review `structured_summary.focus_snippets` first before reading the full export.",
            reason="focus_snippets_available",
        )

    large_method_analysis = export_result.get("large_method_analysis")
    if isinstance(large_method_analysis, dict) and large_method_analysis.get("is_large_method") is True:
        _append_item(
            sections["verifiedFacts"],
            text="The exported method was classified as a large method and includes grouped hotspot compression.",
            evidence=export_evidence,
        )
        _append_item(
            sections["inferences"],
            text="Further manual review should prioritize grouped hotspots instead of scanning the full method linearly.",
            confidence="high",
            evidence=export_evidence,
        )

    if status == "ok" and exact_anchor and export_step is not None:
        _append_item(
            sections["inferences"],
            text="The current summary can be treated as a single exact-method result rather than a mixed overload guess.",
            confidence="high",
            evidence=export_evidence,
        )
    elif status == "ok" and export_step is not None:
        _append_item(
            sections["inferences"],
            text="The current summary is usable as a working baseline, but overload ambiguity still depends on the relaxed anchor context.",
            confidence="medium",
            evidence=export_evidence,
        )


def _populate_search_sections(
    *,
    sections: dict[str, list[dict[str, object]]],
    task_type: str,
    status: str,
    plan: dict[str, object],
    step_results: list[dict[str, object]],
    evidence: list[dict[str, object]],
) -> None:
    step_result = step_results[0] if step_results else None
    result = step_result.get("result") if isinstance(step_result, dict) and isinstance(step_result.get("result"), dict) else {}
    count = result.get("count")
    truncated = result.get("truncated") is True
    method_anchor = {}
    inputs = plan.get("inputs")
    if isinstance(inputs, dict) and isinstance(inputs.get("method_anchor"), dict):
        method_anchor = inputs.get("method_anchor")
    exact_anchor = any(key in method_anchor for key in ("descriptor", "params", "return_type"))

    if exact_anchor and task_type in {"trace_callers", "trace_callees"}:
        _append_item(
            sections["verifiedFacts"],
            text="The relation query anchor is descriptor-aware.",
            reason="method_anchor",
        )

    if isinstance(count, int) and step_result is not None and step_result.get("status") == "ok":
        object_label = {
            "trace_callers": "direct caller hit(s)",
            "trace_callees": "direct callee hit(s)",
        }.get(task_type, "matching method hit(s)")
        _append_item(
            sections["verifiedFacts"],
            text=f"The visible result set contains {count} {object_label}.",
            evidence=_pick_evidence(evidence),
        )
        _append_item(
            sections["inferences"],
            text="The current hits are sufficient to choose the next export or follow-up query target.",
            confidence="medium",
            evidence=_pick_evidence(evidence),
        )

    if truncated:
        _append_item(
            sections["verifiedFacts"],
            text="The returned hit list is truncated to a preview set.",
        )
        _append_item(
            sections["inferences"],
            text="The current hit list should be treated as a narrowing aid, not as a complete inventory.",
            confidence="high",
            evidence=_pick_evidence(evidence),
        )
        _append_item(
            sections["unknowns"],
            text="Additional hits may exist outside the current preview window.",
            reason="result_truncated",
        )
        _append_item(
            sections["nextChecks"],
            text="Narrow the query with `search_package` or `declared_class`, then rerun.",
            reason="narrow_search_after_truncation",
        )

    if status == "empty" and task_type in {"trace_callers", "trace_callees"}:
        _append_item(
            sections["unknowns"],
            text="The current direct-edge query is empty, but indirect relations remain unverified.",
            reason="direct_relation_only",
        )


def _apply_limit_sections(
    *,
    sections: dict[str, list[dict[str, object]]],
    task_type: str,
    limits: list[str],
) -> None:
    for limit in limits:
        if not isinstance(limit, str) or not limit:
            continue
        if "cannot disambiguate overloaded methods" in limit:
            _append_item(
                sections["unknowns"],
                text="The exact overload remains unresolved on the current relaxed summarize path.",
                reason="overload_ambiguity",
            )
            _append_item(
                sections["nextChecks"],
                text="Provide a full descriptor-aware method anchor before rerunning summarize.",
                reason="add_method_descriptor",
            )
        elif "must resolve to exactly one extracted dex" in limit:
            _append_item(
                sections["nextChecks"],
                text="Switch to a direct dex input or narrow the target class before rerunning summarize.",
                reason="narrow_apk_resolution",
            )
        elif "max_direct_hits" in limit and task_type != "summarize_method_logic":
            _append_item(
                sections["unknowns"],
                text="The current preview may omit additional direct hits beyond the capped result window.",
                reason="max_direct_hits",
            )


def _apply_recommendation_sections(
    *,
    sections: dict[str, list[dict[str, object]]],
    recommendations: list[dict[str, object]],
) -> None:
    for recommendation in recommendations:
        if not isinstance(recommendation, dict):
            continue
        message = recommendation.get("message")
        if not isinstance(message, str) or not message:
            continue
        reason = recommendation.get("reason")
        _append_item(
            sections["nextChecks"],
            text=message,
            reason=str(reason) if isinstance(reason, str) and reason else None,
        )


def _apply_generic_status_sections(
    *,
    sections: dict[str, list[dict[str, object]]],
    task_type: str,
    status: str,
    step_results: list[dict[str, object]],
) -> None:
    if status == "execution_error":
        failed_step = _find_first_step_with_status(step_results, "execution_error")
        if failed_step is not None:
            _append_item(
                sections["unknowns"],
                text="The exact root cause still depends on the failed step stderr and raw process logs.",
                reason="execution_error",
            )
            _append_item(
                sections["nextChecks"],
                text=f"Inspect `steps/{failed_step.get('step_id')}/step-result.json` and `raw.stderr.log` for the concrete failure.",
                reason="inspect_failed_step_logs",
            )
        else:
            _append_item(
                sections["unknowns"],
                text="The run failed before a concrete step-level cause was persisted.",
                reason="execution_error",
            )
    elif status == "unsupported":
        _append_item(
            sections["unknowns"],
            text="The current task/input combination is not supported by analyst v1.",
            reason="unsupported",
        )
        _append_item(
            sections["nextChecks"],
            text="Adjust the task type or input shape to a supported analyst v1 path, then rerun.",
            reason="unsupported_path",
        )
    elif status == "input_error":
        _append_item(
            sections["nextChecks"],
            text="Fix the rejected `input-json` fields and rerun the same task.",
            reason="fix_input_json",
        )
    elif status == "ambiguous" and task_type != "summarize_method_logic":
        _append_item(
            sections["unknowns"],
            text="The current query still maps to multiple plausible targets.",
            reason="ambiguous_result",
        )


def _find_first_step(step_results: list[dict[str, object]], step_kind: str) -> dict[str, object] | None:
    for step_result in step_results:
        if isinstance(step_result, dict) and step_result.get("step_kind") == step_kind:
            return step_result
    return None


def _find_first_step_with_status(step_results: list[dict[str, object]], status: str) -> dict[str, object] | None:
    for step_result in step_results:
        if isinstance(step_result, dict) and step_result.get("status") == status:
            return step_result
    return None


def _pick_evidence(
    evidence: list[dict[str, object]],
    *,
    kind: str | None = None,
    source_path: str | None = None,
    limit: int = MAX_EVIDENCE_ITEMS,
) -> list[dict[str, object]]:
    picked: list[dict[str, object]] = []
    for item in evidence:
        if not isinstance(item, dict):
            continue
        item_kind = item.get("kind")
        item_source = item.get("source_path")
        if kind is not None and item_kind != kind:
            continue
        if source_path is not None and item_source != source_path:
            continue
        normalized = _normalize_evidence_item(item)
        if normalized is None:
            continue
        picked.append(normalized)
        if len(picked) >= limit:
            break
    return picked


def _normalize_evidence_item(item: dict[str, object]) -> dict[str, object] | None:
    normalized: dict[str, object] = {}
    for key in ("step_id", "kind", "value", "source_path"):
        value = item.get(key)
        if isinstance(value, str) and value:
            normalized[key] = value
    line_numbers = item.get("line_numbers")
    if isinstance(line_numbers, list):
        normalized_lines = [value for value in line_numbers if isinstance(value, int)]
        if normalized_lines:
            normalized["line_numbers"] = normalized_lines
    return normalized or None


def _append_item(
    bucket: list[dict[str, object]],
    *,
    text: str,
    reason: str | None = None,
    confidence: str | None = None,
    evidence: Iterable[dict[str, object]] | None = None,
) -> None:
    if not isinstance(text, str) or not text.strip():
        return
    payload: dict[str, object] = {"text": text.strip()}
    if isinstance(reason, str) and reason.strip():
        payload["reason"] = reason.strip()
    if isinstance(confidence, str) and confidence.strip():
        payload["confidence"] = confidence.strip()
    if evidence is not None:
        normalized_evidence = [item for item in evidence if isinstance(item, dict) and item]
        if normalized_evidence:
            payload["evidence"] = normalized_evidence
    if any(existing == payload for existing in bucket):
        return
    bucket.append(payload)

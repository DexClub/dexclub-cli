#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone

from planner import PlannerError, build_plan, build_plan_error_payload
from runner import (
    build_analysis_error_result,
    collect_input_paths,
    ensure_run_root,
    make_run_id,
    persist_run_outputs,
    run_plan,
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
    return parser.parse_args()


def load_input_json(raw_value: str) -> dict[str, object]:
    try:
        payload = json.loads(raw_value)
    except json.JSONDecodeError as exc:
        raise PlannerError("input_error", f"`input-json` is not valid JSON: {exc.msg}") from exc
    if not isinstance(payload, dict):
        raise PlannerError("input_error", "`input-json` must decode to a JSON object.")
    return payload


def main() -> None:
    args = parse_args()
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

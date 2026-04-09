#!/usr/bin/env python3
from __future__ import annotations

import json
import subprocess
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from time import perf_counter
from typing import Any, Callable


@dataclass(frozen=True, slots=True)
class ProcessExecutionResult:
    step_id: str
    status: str
    exit_code: int
    command: list[str]
    started_at: str
    finished_at: str
    duration_ms: int
    payload_kind: str
    payload: object | None
    diagnostics: dict[str, object]
    raw_process: dict[str, object]
    artifacts: list[dict[str, object]]
    stdout: str
    stderr: str


def write_text(path: Path, content: str) -> int:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    return len(content.encode("utf-8"))


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


def run_captured_process(
    *,
    step_id: str,
    command: list[str],
    artifact_dir: Path,
    payload_kind: str,
    extractor: Callable[[str], object] | None = None,
) -> ProcessExecutionResult:
    artifact_dir.mkdir(parents=True, exist_ok=True)
    started = datetime.now(timezone.utc).isoformat()
    started_perf = perf_counter()
    completed = subprocess.run(command, capture_output=True, text=True)
    finished = datetime.now(timezone.utc).isoformat()
    duration_ms = int((perf_counter() - started_perf) * 1000)

    stdout_path = artifact_dir / "raw.stdout.log"
    stderr_path = artifact_dir / "raw.stderr.log"
    stdout_size = write_text(stdout_path, completed.stdout)
    stderr_size = write_text(stderr_path, completed.stderr)
    artifacts = [
        {
            "type": "raw_stdout",
            "path": str(stdout_path.resolve()),
            "produced_by_step": step_id,
        },
        {
            "type": "raw_stderr",
            "path": str(stderr_path.resolve()),
            "produced_by_step": step_id,
        },
    ]
    raw_process = {
        "stdout_path": str(stdout_path.resolve()),
        "stderr_path": str(stderr_path.resolve()),
        "stdout_size": stdout_size,
        "stderr_size": stderr_size,
    }
    diagnostics: dict[str, object] = {"message": "Process completed."}
    payload: object | None = None
    status = "ok"

    if completed.returncode != 0:
        status = "execution_error"
        diagnostics = {
            "message": "Process execution failed.",
            "cause": f"Command exited with code {completed.returncode}.",
        }
    elif payload_kind == "json":
        try:
            payload = (extractor or extract_json_payload)(completed.stdout)
        except Exception as exc:
            status = "normalization_error"
            diagnostics = {
                "message": "Process completed but payload normalization failed.",
                "cause": str(exc),
            }
    elif payload_kind == "text":
        payload = completed.stdout
    elif payload_kind != "none":
        raise ValueError(f"Unsupported payload_kind: {payload_kind}")

    return ProcessExecutionResult(
        step_id=step_id,
        status=status,
        exit_code=completed.returncode,
        command=command,
        started_at=started,
        finished_at=finished,
        duration_ms=duration_ms,
        payload_kind=payload_kind,
        payload=payload,
        diagnostics=diagnostics,
        raw_process=raw_process,
        artifacts=artifacts,
        stdout=completed.stdout,
        stderr=completed.stderr,
    )

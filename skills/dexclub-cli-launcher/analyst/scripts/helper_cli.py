#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path
from subprocess import CompletedProcess
from typing import Iterable


def format_actionable_error(
    *,
    summary: str,
    cause: str,
    recommended_action: str,
    notes: Iterable[str] | None = None,
) -> str:
    lines = [
        f"Error: {summary}",
        f"Cause: {cause}",
        f"Recommended action: {recommended_action}",
    ]
    for note in notes or ():
        note_text = str(note).strip()
        if note_text:
            lines.append(f"Note: {note_text}")
    return "\n".join(lines)


class ActionableArgumentParser(argparse.ArgumentParser):
    def __init__(
        self,
        *args,
        error_summary: str,
        recommended_action: str,
        error_notes: Iterable[str] | None = None,
        **kwargs,
    ) -> None:
        self._error_summary = error_summary
        self._recommended_action = recommended_action
        self._error_notes = tuple(error_notes or ())
        super().__init__(*args, **kwargs)

    def error(self, message: str) -> None:
        raise SystemExit(
            format_actionable_error(
                summary=self._error_summary,
                cause=message,
                recommended_action=self._recommended_action,
                notes=self._error_notes,
            )
        )


def first_meaningful_line(*texts: str) -> str | None:
    for text in texts:
        for raw_line in text.splitlines():
            line = raw_line.strip()
            if line:
                return line
    return None


def summarize_process_failure(
    completed: CompletedProcess[str],
    *,
    fallback: str,
) -> str:
    line = first_meaningful_line(completed.stderr, completed.stdout)
    if line:
        return line
    return fallback


def require_existing_file(
    raw_path: str,
    *,
    field_label: str,
    recommended_action: str,
    allowed_suffixes: tuple[str, ...] | None = None,
) -> Path:
    path = Path(raw_path).expanduser().resolve()
    if not path.exists():
        raise SystemExit(
            format_actionable_error(
                summary=f"invalid {field_label}",
                cause=f"{field_label} not found: {path}",
                recommended_action=recommended_action,
            )
        )
    if not path.is_file():
        raise SystemExit(
            format_actionable_error(
                summary=f"invalid {field_label}",
                cause=f"{field_label} is not a file: {path}",
                recommended_action=recommended_action,
            )
        )
    if allowed_suffixes and path.suffix.lower() not in allowed_suffixes:
        allowed_text = ", ".join(allowed_suffixes)
        raise SystemExit(
            format_actionable_error(
                summary=f"invalid {field_label}",
                cause=f"{field_label} must use one of: {allowed_text}. Received: {path.name}",
                recommended_action=recommended_action,
            )
        )
    return path

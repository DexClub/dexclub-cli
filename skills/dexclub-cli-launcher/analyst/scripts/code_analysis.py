#!/usr/bin/env python3
from __future__ import annotations

import re
from pathlib import Path
from typing import Callable

from method_descriptor import parse_method_descriptor

JAVA_CONTROL_KEYWORDS = {
    "if",
    "for",
    "while",
    "switch",
    "catch",
    "return",
    "throw",
    "new",
    "synchronized",
}

STRING_LITERAL_RE = re.compile(r'"((?:\\.|[^"\\])*)"')
NUMBER_LITERAL_RE = re.compile(r"(?<![\w$])(?:-?0x[0-9A-Fa-f]+|-?\d+(?:\.\d+)?)(?![\w$])")
JAVA_CALL_RE = re.compile(r"((?:[A-Za-z_][\w$]*\.)*[A-Za-z_][\w$]*)\s*\(")
JAVA_FIELD_RE = re.compile(r"\b((?:this|super|[A-Za-z_][\w$]*)\.[A-Za-z_][\w$]*)(?!\s*\()")
SMALI_CALL_RE = re.compile(r"invoke-[^\s]+\s+\{[^}]*\},\s+([^\s]+->[^\s]+)")
SMALI_FIELD_RE = re.compile(r"(?:iget|iput|sget|sput)[^\s]*\s+[^,]+,\s+[^,]+,\s+([^\s]+->[^:]+:[^\s]+)")
JAVA_METHOD_DECL_RE = re.compile(
    r"^\s*(?:public|private|protected|static|final|synchronized|abstract|native|\s)+"
    r"[\w<>\[\], ?]+\s+([A-Za-z_][\w$]*)\s*\(",
)


def read_text(path: str | Path) -> str:
    return Path(path).read_text(encoding="utf-8")


def detect_code_kind(path: str | Path, text: str | None = None) -> str:
    path_obj = Path(path)
    if path_obj.suffix.lower() == ".smali":
        return "smali"
    if path_obj.suffix.lower() == ".java":
        return "java"
    if text is None:
        text = read_text(path_obj)
    if ".method " in text and ".end method" in text:
        return "smali"
    return "java"


def scoped_lines(
    path: str | Path,
    method_name: str | None = None,
    method_descriptor: str | None = None,
) -> tuple[str, list[tuple[int, str]]]:
    path_obj = Path(path)
    text = read_text(path_obj)
    kind = detect_code_kind(path_obj, text)
    lines = text.splitlines()
    if not method_name and not method_descriptor:
        return kind, [(index + 1, line) for index, line in enumerate(lines)]
    if method_descriptor and kind != "smali":
        raise ValueError("Exact method descriptor scoping currently requires smali input.")
    if kind == "smali":
        return kind, extract_smali_method_lines(lines, method_name, method_descriptor)
    return kind, extract_java_method_lines(lines, method_name)


def extract_java_method_lines(lines: list[str], method_name: str) -> list[tuple[int, str]]:
    signature_re = re.compile(rf"\b{re.escape(method_name)}\s*\(")
    start_index: int | None = None
    end_index: int | None = None
    depth = 0
    seen_open_brace = False

    for index, line in enumerate(lines):
        stripped = line.strip()
        if start_index is None:
            if stripped.startswith(("//", "*", "@")):
                continue
            if not signature_re.search(line):
                continue
            start_index = index

        open_count = line.count("{")
        close_count = line.count("}")
        if open_count > 0:
            seen_open_brace = True
        depth += open_count
        depth -= close_count

        if start_index is not None and seen_open_brace and depth <= 0:
            end_index = index
            break

    if start_index is None or end_index is None:
        raise ValueError(f"Unable to locate method `{method_name}` in Java source.")
    return [(line_no + 1, lines[line_no]) for line_no in range(start_index, end_index + 1)]


def extract_smali_method_lines(
    lines: list[str],
    method_name: str | None,
    method_descriptor: str | None = None,
) -> list[tuple[int, str]]:
    expected_signature = None
    if method_descriptor:
        parsed = parse_method_descriptor(method_descriptor)
        expected_signature = parsed.smali_signature
        method_name = parsed.method_name
    start_index: int | None = None
    for index, line in enumerate(lines):
        stripped = line.strip()
        if not stripped.startswith(".method"):
            continue
        if expected_signature is not None:
            if expected_signature in stripped:
                start_index = index
                break
            continue
        if method_name and re.search(rf"\b{re.escape(method_name)}\(", stripped):
            start_index = index
            break

    if start_index is None:
        if expected_signature is not None:
            raise ValueError(f"Unable to locate method `{expected_signature}` in smali source.")
        raise ValueError(f"Unable to locate method `{method_name}` in smali source.")

    end_index = start_index
    for index in range(start_index + 1, len(lines)):
        if lines[index].strip() == ".end method":
            end_index = index
            break
    return [(line_no + 1, lines[line_no]) for line_no in range(start_index, end_index + 1)]


def _strip_strings(line: str) -> str:
    return STRING_LITERAL_RE.sub('""', line)


def _collect_occurrences(
    line_entries: list[tuple[int, str]],
    matcher: re.Pattern[str],
    extractor: Callable[[re.Match[str]], str | None],
    *,
    strip_strings: bool = False,
) -> list[dict[str, object]]:
    occurrences: dict[str, dict[str, object]] = {}
    for line_no, line in line_entries:
        target_line = _strip_strings(line) if strip_strings else line
        for match in matcher.finditer(target_line):
            value = extractor(match)
            if not value:
                continue
            entry = occurrences.setdefault(
                value,
                {
                    "value": value,
                    "count": 0,
                    "lines": [],
                },
            )
            entry["count"] = int(entry["count"]) + 1
            lines = entry["lines"]
            if line_no not in lines:
                lines.append(line_no)
    return sorted(
        occurrences.values(),
        key=lambda item: (item["lines"][0] if item["lines"] else 0, str(item["value"])),
    )


def collect_strings(line_entries: list[tuple[int, str]]) -> list[dict[str, object]]:
    return _collect_occurrences(
        line_entries,
        STRING_LITERAL_RE,
        lambda match: match.group(1),
    )


def collect_numbers(line_entries: list[tuple[int, str]]) -> list[dict[str, object]]:
    filtered_entries = []
    for line_no, line in line_entries:
        stripped = line.strip()
        if stripped.startswith((".locals ", ".line ", ".prologue", ".registers ", ".param ", ".end param", ".annotation", ".end annotation")):
            continue
        filtered_entries.append((line_no, line))
    return _collect_occurrences(
        filtered_entries,
        NUMBER_LITERAL_RE,
        lambda match: match.group(0),
        strip_strings=True,
    )


def collect_method_calls(kind: str, line_entries: list[tuple[int, str]]) -> list[dict[str, object]]:
    if kind == "smali":
        return _collect_occurrences(
            line_entries,
            SMALI_CALL_RE,
            lambda match: match.group(1),
        )
    return collect_java_method_calls(line_entries)


def _extract_java_call(match: re.Match[str]) -> str | None:
    target = match.group(1)
    last_name = target.rsplit(".", 1)[-1]
    if last_name in JAVA_CONTROL_KEYWORDS:
        return None
    return target


def collect_java_method_calls(line_entries: list[tuple[int, str]]) -> list[dict[str, object]]:
    occurrences: dict[str, dict[str, object]] = {}
    for line_no, line in line_entries:
        stripped = line.strip()
        if stripped.startswith(("package ", "import ", "//", "*", "@")):
            continue
        declaration_match = JAVA_METHOD_DECL_RE.match(stripped)
        declaration_name = declaration_match.group(1) if declaration_match else None
        target_line = _strip_strings(line)
        for match in JAVA_CALL_RE.finditer(target_line):
            value = _extract_java_call(match)
            if not value:
                continue
            if declaration_name and value.rsplit(".", 1)[-1] == declaration_name:
                continue
            entry = occurrences.setdefault(
                value,
                {
                    "value": value,
                    "count": 0,
                    "lines": [],
                },
            )
            entry["count"] = int(entry["count"]) + 1
            lines = entry["lines"]
            if line_no not in lines:
                lines.append(line_no)
    return sorted(
        occurrences.values(),
        key=lambda item: (item["lines"][0] if item["lines"] else 0, str(item["value"])),
    )


def collect_field_accesses(kind: str, line_entries: list[tuple[int, str]]) -> list[dict[str, object]]:
    if kind == "smali":
        return _collect_occurrences(
            line_entries,
            SMALI_FIELD_RE,
            lambda match: match.group(1),
        )
    filtered_entries = [
        (line_no, line)
        for line_no, line in line_entries
        if not line.strip().startswith(("package ", "import "))
    ]
    return _collect_occurrences(
        filtered_entries,
        JAVA_FIELD_RE,
        lambda match: match.group(1),
        strip_strings=True,
    )


def count_branch_lines(kind: str, line_entries: list[tuple[int, str]]) -> int:
    count = 0
    for _, line in line_entries:
        stripped = line.strip()
        if kind == "smali":
            if stripped.startswith(("if-", "goto", "packed-switch", "sparse-switch")):
                count += 1
            continue
        if stripped.startswith(("if ", "if(", "else if", "switch ", "switch(", "for ", "for(", "while ", "while(", "catch ")):
            count += 1
    return count


def count_return_lines(kind: str, line_entries: list[tuple[int, str]]) -> int:
    count = 0
    for _, line in line_entries:
        stripped = line.strip()
        if kind == "smali":
            if stripped.startswith("return"):
                count += 1
            continue
        if stripped.startswith("return"):
            count += 1
    return count


def analyze_code(
    path: str | Path,
    method_name: str | None = None,
    method_descriptor: str | None = None,
) -> dict[str, object]:
    kind, line_entries = scoped_lines(path, method_name, method_descriptor)
    strings = collect_strings(line_entries)
    numbers = collect_numbers(line_entries)
    method_calls = collect_method_calls(kind, line_entries)
    field_accesses = collect_field_accesses(kind, line_entries)
    return {
        "kind": kind,
        "scope": {
            "path": str(Path(path).resolve()),
            "method": method_name,
            "methodDescriptor": method_descriptor,
            "lineCount": len(line_entries),
            "startLine": line_entries[0][0] if line_entries else None,
            "endLine": line_entries[-1][0] if line_entries else None,
        },
        "strings": strings,
        "numbers": numbers,
        "methodCalls": method_calls,
        "fieldAccesses": field_accesses,
        "branchLineCount": count_branch_lines(kind, line_entries),
        "returnLineCount": count_return_lines(kind, line_entries),
        "code": "\n".join(line for _, line in line_entries),
    }

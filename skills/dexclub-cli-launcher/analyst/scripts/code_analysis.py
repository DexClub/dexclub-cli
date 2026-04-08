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
JAVA_SIGNATURE_MODIFIERS = {
    "public",
    "private",
    "protected",
    "static",
    "final",
    "synchronized",
    "abstract",
    "native",
    "strictfp",
    "default",
}
JAVA_LANGUAGE_SIMPLE_TYPES = {
    "Boolean": "java.lang.Boolean",
    "Byte": "java.lang.Byte",
    "Character": "java.lang.Character",
    "Class": "java.lang.Class",
    "Double": "java.lang.Double",
    "Enum": "java.lang.Enum",
    "Float": "java.lang.Float",
    "Integer": "java.lang.Integer",
    "Long": "java.lang.Long",
    "Object": "java.lang.Object",
    "Short": "java.lang.Short",
    "String": "java.lang.String",
    "Throwable": "java.lang.Throwable",
    "Void": "java.lang.Void",
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
SMALI_BRANCH_RE = re.compile(r"^(if-[^\s]+|goto(?:/[^\s]+)?|packed-switch|sparse-switch)\b")
JAVA_BRANCH_RE = re.compile(r"^(if\b|else if\b|switch\b|for\b|while\b|catch\b)")
SMALI_LABEL_RE = re.compile(r"^(:[A-Za-z0-9_]+)\b")
SMALI_LABEL_TARGET_RE = re.compile(r":[A-Za-z0-9_]+")
JAVA_PACKAGE_RE = re.compile(r"^\s*package\s+([A-Za-z_][\w.]*);")
JAVA_IMPORT_RE = re.compile(r"^\s*import\s+([A-Za-z_][\w.]*)(?:\s*;)$")
JAVA_ANNOTATION_RE = re.compile(r"@\w+(?:\([^)]*\))?\s*")
JAVA_BLOCK_COMMENT_RE = re.compile(r"/\*.*?\*/", re.S)

LARGE_METHOD_LINE_THRESHOLD = 120
HOTSPOT_CLUSTER_LINE_GAP = 8
HOTSPOT_CLUSTER_PADDING = 2
HOTSPOT_PREVIEW_LIMIT = 5
HOTSPOT_CLUSTER_ITEM_LIMIT = 8
HOTSPOT_CLUSTER_LIMIT = 6
STRUCTURED_BLOCK_LIMIT = 24
STRUCTURED_CLUSTER_LIMIT = 8
STRUCTURED_VALUE_LIMIT = 6
STRUCTURED_FOCUS_SNIPPET_LIMIT = 6
STRUCTURED_FOCUS_SNIPPET_LINE_LIMIT = 18


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
    if kind == "smali":
        return kind, extract_smali_method_lines(lines, method_name, method_descriptor)
    return kind, extract_java_method_lines(lines, method_name, method_descriptor)


def extract_java_method_lines(
    lines: list[str],
    method_name: str,
    method_descriptor: str | None = None,
) -> list[tuple[int, str]]:
    expected_descriptor = parse_method_descriptor(method_descriptor) if method_descriptor else None
    package_name, imports = extract_java_import_context(lines)
    signature_re = re.compile(rf"\b{re.escape(method_name)}\s*\(")

    for index, line in enumerate(lines):
        stripped = line.strip()
        if stripped.startswith(("//", "*", "@")):
            continue
        if not signature_re.search(line):
            continue
        signature_end_index = find_java_signature_end(lines, index)
        if signature_end_index is None:
            continue
        signature_text = "\n".join(lines[index:signature_end_index + 1])
        if expected_descriptor is not None and not java_signature_matches(
            signature_text,
            expected_descriptor=expected_descriptor,
            package_name=package_name,
            imports=imports,
        ):
            continue
        end_index = find_java_method_end(lines, index)
        if end_index is None:
            continue
        return [(line_no + 1, lines[line_no]) for line_no in range(index, end_index + 1)]

    if expected_descriptor is not None:
        raise ValueError(f"Unable to locate method `{expected_descriptor.descriptor}` in Java source.")
    raise ValueError(f"Unable to locate method `{method_name}` in Java source.")


def find_java_signature_end(lines: list[str], start_index: int) -> int | None:
    paren_depth = 0
    seen_open_paren = False
    for index in range(start_index, len(lines)):
        line = lines[index]
        paren_depth += line.count("(")
        paren_depth -= line.count(")")
        if "(" in line:
            seen_open_paren = True
        if seen_open_paren and paren_depth <= 0 and "{" in line:
            return index
    return None


def find_java_method_end(lines: list[str], start_index: int) -> int | None:
    depth = 0
    seen_open_brace = False
    for index in range(start_index, len(lines)):
        line = lines[index]
        open_count = line.count("{")
        close_count = line.count("}")
        if open_count > 0:
            seen_open_brace = True
        depth += open_count
        depth -= close_count
        if seen_open_brace and depth <= 0:
            return index
    return None


def extract_java_import_context(lines: list[str]) -> tuple[str | None, dict[str, str]]:
    package_name: str | None = None
    imports: dict[str, str] = {}
    for line in lines:
        package_match = JAVA_PACKAGE_RE.match(line)
        if package_match is not None:
            package_name = package_match.group(1)
            continue
        import_match = JAVA_IMPORT_RE.match(line)
        if import_match is None:
            continue
        full_name = import_match.group(1)
        if full_name.endswith(".*"):
            continue
        imports[full_name.rsplit(".", 1)[-1]] = full_name
    return package_name, imports


def java_signature_matches(
    signature_text: str,
    *,
    expected_descriptor,
    package_name: str | None,
    imports: dict[str, str],
) -> bool:
    cleaned = normalize_java_signature_text(signature_text)
    method_pattern = re.compile(
        rf"(?P<prefix>.*?)\b{re.escape(expected_descriptor.method_name)}\s*\((?P<params>.*)\)\s*\{{",
        re.S,
    )
    match = method_pattern.search(cleaned)
    if match is None:
        return False
    return_type = extract_java_return_type(match.group("prefix"))
    if return_type is None:
        return False
    parsed_params = parse_java_signature_params(
        match.group("params"),
        package_name=package_name,
        imports=imports,
    )
    expected_params = list(expected_descriptor.params)
    if len(parsed_params) != len(expected_params):
        return False
    if not java_types_match(
        resolve_java_type_name(return_type, package_name=package_name, imports=imports),
        expected_descriptor.return_type,
    ):
        return False
    return all(
        java_types_match(actual, expected)
        for actual, expected in zip(parsed_params, expected_params)
    )


def normalize_java_signature_text(signature_text: str) -> str:
    without_comments = JAVA_BLOCK_COMMENT_RE.sub(" ", signature_text)
    without_annotations = JAVA_ANNOTATION_RE.sub("", without_comments)
    return re.sub(r"\s+", " ", without_annotations).strip()


def extract_java_return_type(prefix_text: str) -> str | None:
    cleaned = prefix_text.strip()
    if cleaned.startswith("<"):
        generic_end = cleaned.find(">")
        if generic_end != -1:
            cleaned = cleaned[generic_end + 1:].strip()
    tokens = [token for token in cleaned.split() if token not in JAVA_SIGNATURE_MODIFIERS]
    if not tokens:
        return None
    return tokens[-1]


def parse_java_signature_params(
    params_text: str,
    *,
    package_name: str | None,
    imports: dict[str, str],
) -> list[str]:
    params = split_java_signature_params(params_text)
    resolved: list[str] = []
    for param_text in params:
        type_text = extract_java_param_type(param_text)
        resolved.append(resolve_java_type_name(type_text, package_name=package_name, imports=imports))
    return resolved


def split_java_signature_params(params_text: str) -> list[str]:
    params: list[str] = []
    current: list[str] = []
    generic_depth = 0
    for char in params_text:
        if char == "<":
            generic_depth += 1
        elif char == ">" and generic_depth > 0:
            generic_depth -= 1
        elif char == "," and generic_depth == 0:
            candidate = "".join(current).strip()
            if candidate:
                params.append(candidate)
            current = []
            continue
        current.append(char)
    tail = "".join(current).strip()
    if tail:
        params.append(tail)
    return params


def extract_java_param_type(param_text: str) -> str:
    cleaned = JAVA_ANNOTATION_RE.sub("", param_text)
    cleaned = cleaned.replace("...", "[]")
    cleaned = re.sub(r"\bfinal\s+", "", cleaned)
    cleaned = re.sub(r"\s+", " ", cleaned).strip()
    if " " not in cleaned:
        return cleaned
    return cleaned.rsplit(" ", 1)[0].strip()


def resolve_java_type_name(
    type_text: str,
    *,
    package_name: str | None,
    imports: dict[str, str],
) -> str:
    normalized = type_text.strip()
    if not normalized:
        return normalized
    array_suffix = ""
    while normalized.endswith("[]"):
        normalized = normalized[:-2].strip()
        array_suffix += "[]"
    generic_index = normalized.find("<")
    if generic_index != -1:
        normalized = normalized[:generic_index].strip()
    if normalized in {"void", "boolean", "byte", "short", "char", "int", "long", "float", "double"}:
        return normalized + array_suffix
    if "." in normalized:
        head, tail = normalized.split(".", 1)
        if head in imports:
            return f"{imports[head]}.{tail}{array_suffix}"
        if head[:1].islower():
            return normalized + array_suffix
    if normalized in imports:
        return imports[normalized] + array_suffix
    if normalized in JAVA_LANGUAGE_SIMPLE_TYPES:
        return JAVA_LANGUAGE_SIMPLE_TYPES[normalized] + array_suffix
    if package_name:
        return f"{package_name}.{normalized}{array_suffix}"
    return normalized + array_suffix


def java_types_match(actual_type: str, expected_type: str) -> bool:
    if actual_type == expected_type:
        return True
    return simplify_java_type_name(actual_type) == simplify_java_type_name(expected_type)


def simplify_java_type_name(type_name: str) -> str:
    normalized = type_name.strip()
    array_suffix = ""
    while normalized.endswith("[]"):
        normalized = normalized[:-2].strip()
        array_suffix += "[]"
    normalized = normalized.rsplit(".", 1)[-1]
    normalized = normalized.rsplit("$", 1)[-1]
    return normalized + array_suffix


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


def collect_branch_hotspots(kind: str, line_entries: list[tuple[int, str]]) -> list[dict[str, object]]:
    hotspots: list[dict[str, object]] = []
    for line_no, line in line_entries:
        stripped = line.strip()
        if not stripped:
            continue
        if kind == "smali":
            match = SMALI_BRANCH_RE.match(stripped)
            if not match:
                continue
            hotspots.append(
                {
                    "line": line_no,
                    "opcode": match.group(1),
                    "text": stripped,
                }
            )
            continue
        match = JAVA_BRANCH_RE.match(stripped)
        if not match:
            continue
        hotspots.append(
            {
                "line": line_no,
                "opcode": match.group(1),
                "text": stripped,
            }
        )
    return hotspots


def _lines_to_span(lines: list[int]) -> tuple[int | None, int | None]:
    if not lines:
        return None, None
    return lines[0], lines[-1]


def _preview_occurrence_items(items: list[dict[str, object]], *, limit: int) -> list[dict[str, object]]:
    ordered = sorted(
        items,
        key=lambda item: (
            -(int(item.get("count", 0))),
            item.get("lines", [0])[0] if isinstance(item.get("lines"), list) and item["lines"] else 0,
            str(item.get("value")),
        ),
    )
    preview: list[dict[str, object]] = []
    for item in ordered[:limit]:
        lines = list(item.get("lines", [])) if isinstance(item.get("lines"), list) else []
        start_line, end_line = _lines_to_span(lines)
        preview.append(
            {
                "value": item.get("value"),
                "count": item.get("count", 0),
                "startLine": start_line,
                "endLine": end_line,
                "lines": lines[:limit],
            }
        )
    return preview


def _occurrence_events(kind: str, items: list[dict[str, object]]) -> list[dict[str, object]]:
    events: list[dict[str, object]] = []
    for item in items:
        value = item.get("value")
        lines = item.get("lines", [])
        if not isinstance(lines, list):
            continue
        for line_no in lines:
            if not isinstance(line_no, int):
                continue
            events.append(
                {
                    "kind": kind,
                    "line": line_no,
                    "value": value,
                }
            )
    return events


def _branch_events(items: list[dict[str, object]]) -> list[dict[str, object]]:
    events: list[dict[str, object]] = []
    for item in items:
        line_no = item.get("line")
        if not isinstance(line_no, int):
            continue
        events.append(
            {
                "kind": "branch_hotspots",
                "line": line_no,
                "value": item.get("text"),
                "opcode": item.get("opcode"),
            }
        )
    return events


def _build_event_clusters(
    events: list[dict[str, object]],
    *,
    kind: str,
    scope_start_line: int | None,
    scope_end_line: int | None,
) -> list[dict[str, object]]:
    if not events:
        return []
    ordered = sorted(events, key=lambda item: (int(item["line"]), str(item.get("value"))))
    grouped_events: list[list[dict[str, object]]] = []
    current_group: list[dict[str, object]] = []
    last_line: int | None = None
    for event in ordered:
        line_no = int(event["line"])
        if last_line is None or line_no - last_line <= HOTSPOT_CLUSTER_LINE_GAP:
            current_group.append(event)
        else:
            grouped_events.append(current_group)
            current_group = [event]
        last_line = line_no
    if current_group:
        grouped_events.append(current_group)

    clusters: list[dict[str, object]] = []
    for index, group in enumerate(grouped_events[:HOTSPOT_CLUSTER_LIMIT], start=1):
        focus_lines = sorted({int(event["line"]) for event in group})
        start_line = focus_lines[0]
        end_line = focus_lines[-1]
        if scope_start_line is not None:
            start_line = max(scope_start_line, start_line - HOTSPOT_CLUSTER_PADDING)
        if scope_end_line is not None:
            end_line = min(scope_end_line, end_line + HOTSPOT_CLUSTER_PADDING)
        items: list[dict[str, object]] = []
        for event in group[:HOTSPOT_CLUSTER_ITEM_LIMIT]:
            item = {
                "line": event["line"],
                "value": event.get("value"),
            }
            if event.get("opcode") is not None:
                item["opcode"] = event["opcode"]
            items.append(item)
        clusters.append(
            {
                "clusterId": f"{kind}-{index}",
                "startLine": start_line,
                "endLine": end_line,
                "eventCount": len(group),
                "focusLines": focus_lines,
                "items": items,
            }
        )
    return clusters


def build_large_method_analysis(
    *,
    kind: str,
    scope: dict[str, object],
    strings: list[dict[str, object]],
    numbers: list[dict[str, object]],
    method_calls: list[dict[str, object]],
    field_accesses: list[dict[str, object]],
    branch_hotspots: list[dict[str, object]],
) -> dict[str, object]:
    line_count = int(scope.get("lineCount", 0) or 0)
    is_large_method = kind == "smali" and line_count >= LARGE_METHOD_LINE_THRESHOLD
    analysis: dict[str, object] = {
        "lineThreshold": LARGE_METHOD_LINE_THRESHOLD,
        "isLargeMethod": is_large_method,
        "strategy": "grouped_hotspots_v1" if is_large_method else "none",
        "codeRetained": True,
        "groupCount": 0,
        "groups": [],
    }
    if not is_large_method:
        return analysis

    scope_start_line = scope.get("startLine") if isinstance(scope.get("startLine"), int) else None
    scope_end_line = scope.get("endLine") if isinstance(scope.get("endLine"), int) else None
    groups: list[dict[str, object]] = []
    group_specs = (
        ("method_calls", method_calls, _occurrence_events("method_calls", method_calls)),
        ("strings", strings, _occurrence_events("strings", strings)),
        ("numbers", numbers, _occurrence_events("numbers", numbers)),
        ("field_accesses", field_accesses, _occurrence_events("field_accesses", field_accesses)),
        ("branch_hotspots", branch_hotspots, _branch_events(branch_hotspots)),
    )
    for group_kind, items, events in group_specs:
        if not items:
            continue
        if group_kind == "branch_hotspots":
            top_items = list(items[:HOTSPOT_PREVIEW_LIMIT])
            occurrence_count = len(items)
        else:
            top_items = _preview_occurrence_items(items, limit=HOTSPOT_PREVIEW_LIMIT)
            occurrence_count = sum(int(item.get("count", 0)) for item in items)
        groups.append(
            {
                "kind": group_kind,
                "itemCount": len(items),
                "occurrenceCount": occurrence_count,
                "topItems": top_items,
                "clusters": _build_event_clusters(
                    events,
                    kind=group_kind,
                    scope_start_line=scope_start_line,
                    scope_end_line=scope_end_line,
                ),
            }
        )
    analysis["groupCount"] = len(groups)
    analysis["groups"] = groups
    return analysis


def _occurrence_line_map(items: list[dict[str, object]]) -> dict[int, list[str]]:
    line_map: dict[int, list[str]] = {}
    for item in items:
        value = item.get("value")
        lines = item.get("lines")
        if value is None or not isinstance(lines, list):
            continue
        for line_no in lines:
            if not isinstance(line_no, int):
                continue
            bucket = line_map.setdefault(line_no, [])
            if value not in bucket:
                bucket.append(str(value))
    return line_map


def _branch_line_map(items: list[dict[str, object]]) -> dict[int, list[dict[str, str]]]:
    line_map: dict[int, list[dict[str, str]]] = {}
    for item in items:
        line_no = item.get("line")
        opcode = item.get("opcode")
        text = item.get("text")
        if not isinstance(line_no, int) or not isinstance(opcode, str) or not isinstance(text, str):
            continue
        line_map.setdefault(line_no, []).append(
            {
                "opcode": opcode,
                "text": text,
            }
        )
    return line_map


def _unique_preserving_order(values: list[str], *, limit: int | None = None) -> list[str]:
    result: list[str] = []
    for value in values:
        if value in result:
            continue
        result.append(value)
        if limit is not None and len(result) >= limit:
            break
    return result


def _collect_block_values(line_numbers: list[int], line_map: dict[int, list[str]], *, limit: int) -> list[str]:
    values: list[str] = []
    for line_no in line_numbers:
        values.extend(line_map.get(line_no, []))
    return _unique_preserving_order(values, limit=limit)


def _line_matches_smali_leader(stripped: str) -> bool:
    return bool(SMALI_LABEL_RE.match(stripped))


def _line_starts_new_smali_block(stripped: str) -> bool:
    if not stripped:
        return False
    if _line_matches_smali_leader(stripped):
        return True
    return False


def _is_smali_transfer_instruction(stripped: str) -> bool:
    if not stripped:
        return False
    if stripped.startswith(("return", "throw", "goto", "if-", "packed-switch", "sparse-switch")):
        return True
    return False


def _build_smali_basic_blocks(
    line_entries: list[tuple[int, str]],
    *,
    strings: list[dict[str, object]],
    numbers: list[dict[str, object]],
    method_calls: list[dict[str, object]],
    field_accesses: list[dict[str, object]],
    branch_hotspots: list[dict[str, object]],
) -> list[dict[str, object]]:
    effective_entries = [
        (line_no, line)
        for line_no, line in line_entries
        if line.strip() != ".end method"
    ]
    if not effective_entries:
        return []

    string_map = _occurrence_line_map(strings)
    number_map = _occurrence_line_map(numbers)
    call_map = _occurrence_line_map(method_calls)
    field_map = _occurrence_line_map(field_accesses)
    branch_map = _branch_line_map(branch_hotspots)

    leaders = {0}
    for index, (_, line) in enumerate(effective_entries):
        stripped = line.strip()
        if index > 0 and _line_starts_new_smali_block(stripped):
            leaders.add(index)
        if index + 1 < len(effective_entries) and _is_smali_transfer_instruction(stripped):
            leaders.add(index + 1)

    ordered_leaders = sorted(leaders)
    basic_blocks: list[dict[str, object]] = []
    line_to_block_id: dict[int, str] = {}

    for block_index, start_index in enumerate(ordered_leaders):
        end_index = (
            ordered_leaders[block_index + 1] - 1
            if block_index + 1 < len(ordered_leaders)
            else len(effective_entries) - 1
        )
        block_entries = effective_entries[start_index:end_index + 1]
        line_numbers = [line_no for line_no, _ in block_entries]
        labels = [
            match.group(1)
            for _, line in block_entries
            for match in [SMALI_LABEL_RE.match(line.strip())]
            if match
        ]
        branch_entries = [
            {
                "line": line_no,
                **branch,
            }
            for line_no in line_numbers
            for branch in branch_map.get(line_no, [])
        ]
        branch_opcodes = _unique_preserving_order(
            [entry["opcode"] for entry in branch_entries],
            limit=STRUCTURED_VALUE_LIMIT,
        )
        last_meaningful_line = ""
        for _, line in reversed(block_entries):
            stripped = line.strip()
            if stripped:
                last_meaningful_line = stripped
                break
        terminator_kind = "fallthrough"
        if last_meaningful_line.startswith("return"):
            terminator_kind = "return"
        elif last_meaningful_line.startswith("throw"):
            terminator_kind = "throw"
        elif last_meaningful_line.startswith("goto"):
            terminator_kind = "goto"
        elif last_meaningful_line.startswith("if-"):
            terminator_kind = "branch"
        elif last_meaningful_line.startswith(("packed-switch", "sparse-switch")):
            terminator_kind = "switch"

        if terminator_kind in {"goto", "branch", "switch"}:
            branch_targets = _unique_preserving_order(
                SMALI_LABEL_TARGET_RE.findall(last_meaningful_line),
                limit=STRUCTURED_VALUE_LIMIT,
            )
        else:
            branch_targets = []
        block_id = f"bb-{block_index + 1}"
        next_block_id = f"bb-{block_index + 2}" if block_index + 1 < len(ordered_leaders) else None
        for line_no in line_numbers:
            line_to_block_id[line_no] = block_id
        basic_blocks.append(
            {
                "blockId": block_id,
                "startLine": line_numbers[0],
                "endLine": line_numbers[-1],
                "lineCount": len(line_numbers),
                "labels": labels,
                "terminatorKind": terminator_kind,
                "branchTargets": branch_targets,
                "nextBlockId": next_block_id,
                "methodCalls": _collect_block_values(line_numbers, call_map, limit=STRUCTURED_VALUE_LIMIT),
                "strings": _collect_block_values(line_numbers, string_map, limit=STRUCTURED_VALUE_LIMIT),
                "numbers": _collect_block_values(line_numbers, number_map, limit=STRUCTURED_VALUE_LIMIT),
                "fieldAccesses": _collect_block_values(line_numbers, field_map, limit=STRUCTURED_VALUE_LIMIT),
                "branchOpcodes": branch_opcodes,
                "summaryTags": _unique_preserving_order(
                    [
                        *(
                            ["calls"]
                            if _collect_block_values(line_numbers, call_map, limit=1)
                            else []
                        ),
                        *(
                            ["constants"]
                            if (
                                _collect_block_values(line_numbers, string_map, limit=1)
                                or _collect_block_values(line_numbers, number_map, limit=1)
                            )
                            else []
                        ),
                        *(
                            ["fields"]
                            if _collect_block_values(line_numbers, field_map, limit=1)
                            else []
                        ),
                        *(["branches"] if branch_opcodes else []),
                        *(["entry"] if block_index == 0 else []),
                        *(
                            ["exit"]
                            if terminator_kind in {"return", "throw"}
                            else []
                        ),
                    ],
                    limit=STRUCTURED_VALUE_LIMIT,
                ),
            }
        )

    label_to_block_id = {
        label: block["blockId"]
        for block in basic_blocks
        for label in block["labels"]
    }
    for block in basic_blocks:
        branch_target_block_ids = [
            label_to_block_id[label]
            for label in block["branchTargets"]
            if label in label_to_block_id
        ]
        if block["terminatorKind"] in {"return", "throw"}:
            successor_block_ids: list[str] = []
        elif block["terminatorKind"] == "goto":
            successor_block_ids = branch_target_block_ids
        elif block["terminatorKind"] in {"branch", "switch"}:
            successor_block_ids = list(branch_target_block_ids)
            next_block_id = block.get("nextBlockId")
            if isinstance(next_block_id, str) and next_block_id not in successor_block_ids:
                successor_block_ids.append(next_block_id)
        else:
            next_block_id = block.get("nextBlockId")
            successor_block_ids = [next_block_id] if isinstance(next_block_id, str) else []
        block["successorBlockIds"] = successor_block_ids

    return basic_blocks


def _cluster_relevant_blocks(
    basic_blocks: list[dict[str, object]],
    *,
    relevance: Callable[[dict[str, object]], bool],
) -> list[list[dict[str, object]]]:
    relevant_blocks = [block for block in basic_blocks if relevance(block)]
    if not relevant_blocks:
        return []
    clusters: list[list[dict[str, object]]] = []
    current: list[dict[str, object]] = []
    last_block_number: int | None = None
    for block in relevant_blocks:
        block_id = str(block["blockId"])
        block_number = int(block_id.split("-")[-1])
        if last_block_number is None or block_number - last_block_number <= 1:
            current.append(block)
        else:
            clusters.append(current)
            current = [block]
        last_block_number = block_number
    if current:
        clusters.append(current)
    return clusters


def _summarize_block_cluster(
    blocks: list[dict[str, object]],
    *,
    cluster_id: str,
    value_keys: list[tuple[str, str]],
) -> dict[str, object]:
    start_line = min(int(block["startLine"]) for block in blocks)
    end_line = max(int(block["endLine"]) for block in blocks)
    payload: dict[str, object] = {
        "clusterId": cluster_id,
        "startLine": start_line,
        "endLine": end_line,
        "blockIds": [str(block["blockId"]) for block in blocks],
        "blockCount": len(blocks),
        "terminatorKinds": _unique_preserving_order(
            [str(block["terminatorKind"]) for block in blocks],
            limit=STRUCTURED_VALUE_LIMIT,
        ),
        "branchOpcodes": _unique_preserving_order(
            [opcode for block in blocks for opcode in block.get("branchOpcodes", [])],
            limit=STRUCTURED_VALUE_LIMIT,
        ),
    }
    for output_key, block_key in value_keys:
        payload[output_key] = _unique_preserving_order(
            [value for block in blocks for value in block.get(block_key, [])],
            limit=STRUCTURED_VALUE_LIMIT,
        )
    return payload


def _build_focus_snippet(
    line_entries: list[tuple[int, str]],
    *,
    source_kind: str,
    source_id: str,
    start_line: int,
    end_line: int,
) -> dict[str, object]:
    scoped_entries = [
        (line_no, line)
        for line_no, line in line_entries
        if start_line <= line_no <= end_line and line.strip() != ".end method"
    ]
    total_line_count = len(scoped_entries)
    if total_line_count <= STRUCTURED_FOCUS_SNIPPET_LINE_LIMIT:
        selected_entries = scoped_entries
        truncated = False
    else:
        head_count = STRUCTURED_FOCUS_SNIPPET_LINE_LIMIT // 2
        tail_count = STRUCTURED_FOCUS_SNIPPET_LINE_LIMIT - head_count
        selected_entries = scoped_entries[:head_count] + [(-1, "...")] + scoped_entries[-tail_count:]
        truncated = True

    code_lines: list[str] = []
    selected_start_line: int | None = None
    selected_end_line: int | None = None
    for line_no, line in selected_entries:
        if line_no == -1:
            code_lines.append("...")
            continue
        if selected_start_line is None:
            selected_start_line = line_no
        selected_end_line = line_no
        code_lines.append(f"{line_no}: {line}")

    return {
        "sourceKind": source_kind,
        "sourceId": source_id,
        "startLine": start_line,
        "endLine": end_line,
        "selectedStartLine": selected_start_line,
        "selectedEndLine": selected_end_line,
        "lineCount": total_line_count,
        "truncated": truncated,
        "code": "\n".join(code_lines),
    }


def _build_focus_snippets(
    *,
    line_entries: list[tuple[int, str]],
    basic_blocks: list[dict[str, object]],
    call_clusters: list[dict[str, object]],
    constant_clusters: list[dict[str, object]],
) -> tuple[list[dict[str, object]], bool]:
    focus_requests: list[tuple[str, str, int, int]] = []
    for block in basic_blocks:
        summary_tags = block.get("summaryTags", [])
        if not isinstance(summary_tags, list):
            continue
        if "branches" in summary_tags or ("calls" in summary_tags and "entry" in summary_tags):
            focus_requests.append(
                (
                    "basic_block",
                    str(block["blockId"]),
                    int(block["startLine"]),
                    int(block["endLine"]),
                )
            )
        if len(focus_requests) >= 2:
            break

    for cluster in call_clusters[:2]:
        focus_requests.append(
            (
                "call_cluster",
                str(cluster["clusterId"]),
                int(cluster["startLine"]),
                int(cluster["endLine"]),
            )
        )
    for cluster in constant_clusters[:2]:
        focus_requests.append(
            (
                "constant_cluster",
                str(cluster["clusterId"]),
                int(cluster["startLine"]),
                int(cluster["endLine"]),
            )
        )

    snippets: list[dict[str, object]] = []
    seen_keys: set[tuple[str, int, int]] = set()
    truncated = False
    for source_kind, source_id, start_line, end_line in focus_requests:
        key = (source_kind, start_line, end_line)
        if key in seen_keys:
            continue
        seen_keys.add(key)
        snippets.append(
            _build_focus_snippet(
                line_entries,
                source_kind=source_kind,
                source_id=source_id,
                start_line=start_line,
                end_line=end_line,
            )
        )
        if len(snippets) >= STRUCTURED_FOCUS_SNIPPET_LIMIT:
            truncated = len(focus_requests) > STRUCTURED_FOCUS_SNIPPET_LIMIT
            break
    return snippets, truncated


def build_structured_summary(
    *,
    kind: str,
    line_entries: list[tuple[int, str]],
    strings: list[dict[str, object]],
    numbers: list[dict[str, object]],
    method_calls: list[dict[str, object]],
    field_accesses: list[dict[str, object]],
    branch_hotspots: list[dict[str, object]],
) -> dict[str, object]:
    if kind != "smali":
        return {
            "kind": "none",
            "supported": False,
            "reason": "structured summary currently only supports smali",
            "basicBlockCount": 0,
            "basicBlocks": [],
            "callClusterCount": 0,
            "callClusters": [],
            "constantClusterCount": 0,
            "constantClusters": [],
            "focusSnippetCount": 0,
            "focusSnippets": [],
        }

    basic_blocks = _build_smali_basic_blocks(
        line_entries,
        strings=strings,
        numbers=numbers,
        method_calls=method_calls,
        field_accesses=field_accesses,
        branch_hotspots=branch_hotspots,
    )
    call_cluster_groups = _cluster_relevant_blocks(
        basic_blocks,
        relevance=lambda block: bool(block.get("methodCalls")),
    )
    constant_cluster_groups = _cluster_relevant_blocks(
        basic_blocks,
        relevance=lambda block: bool(block.get("strings") or block.get("numbers")),
    )
    call_clusters = [
        _summarize_block_cluster(
            blocks,
            cluster_id=f"call-cluster-{index}",
            value_keys=[
                ("methodCalls", "methodCalls"),
                ("strings", "strings"),
                ("numbers", "numbers"),
                ("fieldAccesses", "fieldAccesses"),
            ],
        )
        for index, blocks in enumerate(
            call_cluster_groups[:STRUCTURED_CLUSTER_LIMIT],
            start=1,
        )
    ]
    constant_clusters = [
        _summarize_block_cluster(
            blocks,
            cluster_id=f"constant-cluster-{index}",
            value_keys=[
                ("strings", "strings"),
                ("numbers", "numbers"),
                ("methodCalls", "methodCalls"),
                ("fieldAccesses", "fieldAccesses"),
            ],
        )
        for index, blocks in enumerate(
            constant_cluster_groups[:STRUCTURED_CLUSTER_LIMIT],
            start=1,
        )
    ]
    exit_block_ids = [
        str(block["blockId"])
        for block in basic_blocks
        if block.get("terminatorKind") in {"return", "throw"}
    ]
    focus_snippets, focus_snippets_truncated = _build_focus_snippets(
        line_entries=line_entries,
        basic_blocks=basic_blocks,
        call_clusters=call_clusters,
        constant_clusters=constant_clusters,
    )
    return {
        "kind": "smali_block_outline_v1",
        "supported": True,
        "basicBlockCount": len(basic_blocks),
        "basicBlocksTruncated": len(basic_blocks) > STRUCTURED_BLOCK_LIMIT,
        "basicBlocks": basic_blocks[:STRUCTURED_BLOCK_LIMIT],
        "entryBlockId": str(basic_blocks[0]["blockId"]) if basic_blocks else None,
        "exitBlockIds": exit_block_ids,
        "callClusterCount": len(call_cluster_groups),
        "callClustersTruncated": len(call_cluster_groups) > STRUCTURED_CLUSTER_LIMIT,
        "callClusters": call_clusters,
        "constantClusterCount": len(constant_cluster_groups),
        "constantClustersTruncated": len(constant_cluster_groups) > STRUCTURED_CLUSTER_LIMIT,
        "constantClusters": constant_clusters,
        "focusSnippetCount": len(focus_snippets),
        "focusSnippetsTruncated": focus_snippets_truncated,
        "focusSnippets": focus_snippets,
    }


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
    branch_hotspots = collect_branch_hotspots(kind, line_entries)
    scope = {
        "path": str(Path(path).resolve()),
        "method": method_name,
        "methodDescriptor": method_descriptor,
        "lineCount": len(line_entries),
        "startLine": line_entries[0][0] if line_entries else None,
        "endLine": line_entries[-1][0] if line_entries else None,
    }
    return {
        "kind": kind,
        "scope": scope,
        "strings": strings,
        "numbers": numbers,
        "methodCalls": method_calls,
        "fieldAccesses": field_accesses,
        "branchHotspots": branch_hotspots,
        "branchLineCount": count_branch_lines(kind, line_entries),
        "returnLineCount": count_return_lines(kind, line_entries),
        "structuredSummary": build_structured_summary(
            kind=kind,
            line_entries=line_entries,
            strings=strings,
            numbers=numbers,
            method_calls=method_calls,
            field_accesses=field_accesses,
            branch_hotspots=branch_hotspots,
        ),
        "largeMethodAnalysis": build_large_method_analysis(
            kind=kind,
            scope=scope,
            strings=strings,
            numbers=numbers,
            method_calls=method_calls,
            field_accesses=field_accesses,
            branch_hotspots=branch_hotspots,
        ),
        "code": "\n".join(line for _, line in line_entries),
    }

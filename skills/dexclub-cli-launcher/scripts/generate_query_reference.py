#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import re
import sys
from dataclasses import dataclass
from pathlib import Path


@dataclass
class FieldDef:
    name: str
    type_text: str
    optional: bool


@dataclass
class TypeDef:
    name: str
    kind: str
    fields: list[FieldDef]
    enum_values: list[str]


ROOT_REQUESTS = ("FindClass", "FindMethod", "FindField")
DEXKIT_SOURCE_RELATIVE_DIR = Path("dexkit/src/commonMain/kotlin/io/github/dexclub/dexkit")
QUERY_SOURCE_DIR_ENV = "DEXCLUB_CLI_QUERY_SOURCE_DIR"
REPO_ROOT_ENV = "DEXCLUB_CLI_REPO_ROOT"


def strip_comments(text: str) -> str:
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    text = re.sub(r"//.*", "", text)
    return text


def normalize_type(type_text: str) -> str:
    return " ".join(type_text.replace("\n", " ").split())


def split_top_level(text: str) -> list[str]:
    parts: list[str] = []
    current: list[str] = []
    depth_angle = 0
    depth_paren = 0
    depth_brace = 0
    for char in text:
        if char == "<":
            depth_angle += 1
        elif char == ">":
            depth_angle = max(depth_angle - 1, 0)
        elif char == "(":
            depth_paren += 1
        elif char == ")":
            depth_paren = max(depth_paren - 1, 0)
        elif char == "{":
            depth_brace += 1
        elif char == "}":
            depth_brace = max(depth_brace - 1, 0)
        elif char == "," and depth_angle == 0 and depth_paren == 0 and depth_brace == 0:
            part = "".join(current).strip()
            if part:
                parts.append(part)
            current = []
            continue
        current.append(char)
    tail = "".join(current).strip()
    if tail:
        parts.append(tail)
    return parts


def find_matching(text: str, start_index: int, open_char: str, close_char: str) -> int:
    depth = 0
    for index in range(start_index, len(text)):
        char = text[index]
        if char == open_char:
            depth += 1
        elif char == close_char:
            depth -= 1
            if depth == 0:
                return index
    raise ValueError(f"Unmatched {open_char} in declaration")


def iter_declarations(text: str) -> list[TypeDef]:
    declarations: list[TypeDef] = []
    token_pattern = re.compile(r"\b(enum class|data class|class)\s+(\w+)")
    for match in token_pattern.finditer(text):
        keyword = match.group(1)
        name = match.group(2)
        if keyword == "enum class":
            brace_start = text.find("{", match.end())
            if brace_start < 0:
                continue
            brace_end = find_matching(text, brace_start, "{", "}")
            raw_values = text[brace_start + 1:brace_end]
            values = [value.strip() for value in raw_values.split(",") if value.strip()]
            declarations.append(TypeDef(name=name, kind="enum", fields=[], enum_values=values))
            continue

        paren_start = text.find("(", match.end())
        if paren_start < 0:
            continue
        paren_end = find_matching(text, paren_start, "(", ")")
        constructor = text[paren_start + 1:paren_end]
        fields: list[FieldDef] = []
        for part in split_top_level(constructor):
            cleaned = re.sub(r"@\w+(?:\([^)]*\))?\s*", "", part).strip()
            field_match = re.match(r"(?:internal\s+)?(?:override\s+)?(?:val|var)\s+(\w+):\s*(.+)", cleaned, flags=re.S)
            if not field_match:
                continue
            field_name = field_match.group(1)
            remainder = field_match.group(2).strip()
            default_index = None
            depth_angle = 0
            depth_paren = 0
            depth_brace = 0
            for index, char in enumerate(remainder):
                if char == "<":
                    depth_angle += 1
                elif char == ">":
                    depth_angle = max(depth_angle - 1, 0)
                elif char == "(":
                    depth_paren += 1
                elif char == ")":
                    depth_paren = max(depth_paren - 1, 0)
                elif char == "{":
                    depth_brace += 1
                elif char == "}":
                    depth_brace = max(depth_brace - 1, 0)
                elif char == "=" and depth_angle == 0 and depth_paren == 0 and depth_brace == 0:
                    default_index = index
                    break
            type_text = remainder if default_index is None else remainder[:default_index].strip()
            type_text = normalize_type(type_text)
            fields.append(
                FieldDef(
                    name=field_name,
                    type_text=type_text,
                    optional=type_text.endswith("?"),
                )
            )
        declarations.append(TypeDef(name=name, kind="class", fields=fields, enum_values=[]))
    return declarations


def extract_class_defs(directory: Path) -> dict[str, TypeDef]:
    type_defs: dict[str, TypeDef] = {}
    for path in sorted(directory.glob("*.kt")):
        text = strip_comments(path.read_text(encoding="utf-8"))
        for declaration in iter_declarations(text):
            type_defs[declaration.name] = declaration
    return type_defs


def validate_source_dirs(base_dir: Path) -> tuple[Path, Path] | None:
    resolved_base_dir = base_dir.resolve()
    query_dir = resolved_base_dir / "query"
    result_dir = resolved_base_dir / "result"
    if query_dir.is_dir() and result_dir.is_dir():
        return query_dir, result_dir
    return None


def fail_missing_sources() -> None:
    expected_tree = "dexkit/src/commonMain/kotlin/io/github/dexclub/dexkit/{query,result}"
    print(
        "Unable to locate dexkit query sources for reference generation.\n"
        f"Set {QUERY_SOURCE_DIR_ENV} to the dexkit source directory that contains query/ and result/, "
        f"or set {REPO_ROOT_ENV} to a repository root containing {expected_tree}.\n"
        "The skill itself can still run with the pre-generated references in references/query-json.md "
        "and references/query-json.schema.json.",
        file=sys.stderr,
    )
    raise SystemExit(1)


def resolve_source_dirs(skill_dir: Path) -> tuple[Path, Path]:
    query_source_dir = os.environ.get(QUERY_SOURCE_DIR_ENV)
    if query_source_dir:
        resolved = validate_source_dirs(Path(query_source_dir).expanduser())
        if resolved is not None:
            return resolved
        fail_missing_sources()

    repo_root = os.environ.get(REPO_ROOT_ENV)
    if repo_root:
        resolved = validate_source_dirs(Path(repo_root).expanduser() / DEXKIT_SOURCE_RELATIVE_DIR)
        if resolved is not None:
            return resolved
        fail_missing_sources()

    resolved = validate_source_dirs(skill_dir.parent.parent / DEXKIT_SOURCE_RELATIVE_DIR)
    if resolved is not None:
        return resolved
    fail_missing_sources()


def unwrap_nullable(type_text: str) -> tuple[str, bool]:
    if type_text.endswith("?"):
        return type_text[:-1].strip(), True
    return type_text, False


def unwrap_generic(type_text: str, wrapper_names: tuple[str, ...]) -> str | None:
    for wrapper in wrapper_names:
        prefix = f"{wrapper}<"
        if type_text.startswith(prefix) and type_text.endswith(">"):
            return type_text[len(prefix):-1].strip()
    return None


def split_generic_args(type_text: str) -> list[str]:
    return split_top_level(type_text)


def to_schema_ref(name: str) -> dict[str, str]:
    return {"$ref": f"#/$defs/{name}"}


def primitive_schema(type_name: str) -> dict[str, object] | None:
    mapping = {
        "String": {"type": "string"},
        "Char": {"type": "string", "minLength": 1, "maxLength": 1},
        "Boolean": {"type": "boolean"},
        "Int": {"type": "integer"},
        "Long": {"type": "integer"},
        "Short": {"type": "integer"},
        "Byte": {"type": "integer"},
        "Float": {"type": "number"},
        "Double": {"type": "number"},
    }
    return mapping.get(type_name)


def schema_for_type(type_text: str, known_types: set[str]) -> dict[str, object]:
    base_type, nullable = unwrap_nullable(type_text)

    if base_type == "IntRange":
        schema: dict[str, object] = {
            "type": "object",
            "properties": {
                "start": {"type": "integer"},
                "endInclusive": {"type": "integer"},
            },
            "required": ["start", "endInclusive"],
            "additionalProperties": False,
        }
    elif (item_type := unwrap_generic(base_type, ("List", "MutableList"))) is not None:
        item_schema = schema_for_type(item_type, known_types)
        schema = {"type": "array", "items": item_schema}
    elif (value_types := unwrap_generic(base_type, ("Map", "MutableMap"))) is not None:
        key_type, value_type = split_generic_args(value_types)
        if key_type != "String":
            schema = {"type": "object"}
        else:
            schema = {
                "type": "object",
                "additionalProperties": schema_for_type(value_type, known_types),
            }
    elif base_type in known_types:
        schema = to_schema_ref(base_type)
    elif (primitive := primitive_schema(base_type)) is not None:
        schema = primitive
    else:
        schema = {"type": "object"}

    if nullable:
        if "$ref" in schema:
            return {"anyOf": [schema, {"type": "null"}]}
        schema = dict(schema)
        existing_type = schema.get("type")
        if isinstance(existing_type, str):
            schema["type"] = [existing_type, "null"]
        elif isinstance(existing_type, list):
            schema["type"] = list(existing_type) + ["null"]
        else:
            schema = {"anyOf": [schema, {"type": "null"}]}
    return schema


def build_schema(type_defs: dict[str, TypeDef]) -> dict[str, object]:
    known_types = set(type_defs)
    defs: dict[str, object] = {}
    for name, type_def in type_defs.items():
        if type_def.kind == "enum":
            defs[name] = {
                "type": "string",
                "enum": type_def.enum_values,
            }
            continue

        properties: dict[str, object] = {}
        required: list[str] = []
        for field in type_def.fields:
            properties[field.name] = schema_for_type(field.type_text, known_types)
            if not field.optional:
                required.append(field.name)
        defs[name] = {
            "type": "object",
            "properties": properties,
            "required": required,
            "additionalProperties": False,
        }
    return {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "title": "dexclub-cli Query JSON",
        "oneOf": [to_schema_ref(name) for name in ROOT_REQUESTS],
        "$defs": defs,
    }


def type_display(type_text: str) -> str:
    return type_text.replace("MutableList", "List").replace("MutableMap", "Map")


def render_markdown(query_defs: dict[str, TypeDef], result_defs: dict[str, TypeDef]) -> str:
    lines: list[str] = []
    lines.append("# Query JSON Reference")
    lines.append("")
    lines.append("> Generated by `scripts/generate_query_reference.py`. Do not hand-edit this file.")
    lines.append("> Regeneration requires the dexkit query sources. Standalone skill usage relies on this pre-generated file.")
    lines.append("")
    lines.append("Use this reference when you need to write or explain `find-class`, `find-method`, or `find-field` JSON queries without reading repository source code.")
    lines.append("")
    lines.append("## Root requests")
    lines.append("")
    for root_name in ROOT_REQUESTS:
        root_def = query_defs[root_name]
        lines.append(f"### `{root_name}`")
        lines.append("")
        for field in root_def.fields:
            lines.append(f"- `{field.name}`: `{type_display(field.type_text)}`")
        lines.append("")

    lines.append("## Query types")
    lines.append("")
    for name in sorted(query_defs):
        if name in ROOT_REQUESTS:
            continue
        type_def = query_defs[name]
        lines.append(f"### `{name}`")
        lines.append("")
        if type_def.kind == "enum":
            for value in type_def.enum_values:
                lines.append(f"- `{value}`")
            lines.append("")
            continue

        if not type_def.fields:
            lines.append("- No public constructor fields.")
            lines.append("")
            continue

        for field in type_def.fields:
            lines.append(f"- `{field.name}`: `{type_display(field.type_text)}`")
        lines.append("")

    lines.append("## Result types used by root requests")
    lines.append("")
    for name in sorted(result_defs):
        type_def = result_defs[name]
        if type_def.kind != "class":
            continue
        lines.append(f"### `{name}`")
        lines.append("")
        for field in type_def.fields:
            lines.append(f"- `{field.name}`: `{type_display(field.type_text)}`")
        lines.append("")

    lines.append("## Guidance")
    lines.append("")
    lines.append("- For `usingStrings`, prefer `Contains` with `ignoreCase: true` unless exact matching is explicitly required.")
    lines.append("- For class, method, field, and type names, `Equals` is usually the better default.")
    lines.append("- When the user asks which methods call a known target method, prefer `invokeMethods` on candidate callers.")
    lines.append("- Use `searchPackages` before adding deeply nested matcher branches when the target package is known.")
    lines.append("")
    lines.append("## Schema")
    lines.append("")
    lines.append("- Machine-readable schema: `references/query-json.schema.json`")
    lines.append("")
    return "\n".join(lines)


def main() -> None:
    script_dir = Path(__file__).resolve().parent
    skill_dir = script_dir.parent
    query_dir, result_dir = resolve_source_dirs(skill_dir)
    references_dir = skill_dir / "references"
    references_dir.mkdir(parents=True, exist_ok=True)

    query_defs = extract_class_defs(query_dir)
    result_defs = extract_class_defs(result_dir)
    schema = build_schema({**query_defs, **result_defs})
    markdown = render_markdown(query_defs, result_defs)

    (references_dir / "query-json.md").write_text(markdown, encoding="utf-8")
    (references_dir / "query-json.schema.json").write_text(
        json.dumps(schema, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()

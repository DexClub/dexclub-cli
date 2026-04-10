#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

from method_descriptor import parse_method_descriptor

STRING_MATCH_TYPES = ("Contains", "Equals", "StartsWith", "EndsWith", "SimilarRegex")
COLLECTION_MATCH_TYPES = ("Contains", "Equals")


def parse_number_literal(value: str) -> dict[str, Any]:
    raw = value.strip()
    if not raw:
        raise ValueError("Numeric matcher values must not be empty.")
    negative = raw.startswith("-")
    normalized = raw[1:] if negative else raw
    if normalized.lower().startswith("0x"):
        parsed = int(raw, 16)
        if -(2**31) <= parsed <= (2**31 - 1):
            return {"intValue": parsed}
        return {"longValue": parsed}
    if any(char in raw for char in ".eE"):
        return {"doubleValue": float(raw)}
    parsed = int(raw, 10)
    if -(2**31) <= parsed <= (2**31 - 1):
        return {"intValue": parsed}
    return {"longValue": parsed}


def string_matcher(value: str, match_type: str, ignore_case: bool) -> dict[str, Any]:
    return {
        "value": value,
        "matchType": match_type,
        "ignoreCase": ignore_case,
    }


def maybe_add(target: dict[str, Any], key: str, value: Any) -> None:
    if value is None:
        return
    if isinstance(value, (list, dict)) and not value:
        return
    target[key] = value


def class_name_matcher(
    value: str | None,
    *,
    match_type: str,
    ignore_case: bool,
) -> dict[str, Any] | None:
    if not value:
        return None
    return {
        "className": string_matcher(value, match_type, ignore_case),
    }


def parameter_matchers(values: list[str]) -> dict[str, Any] | None:
    if not values:
        return None
    return {
        "params": [
            {
                "type": {
                    "className": string_matcher(value, "Equals", False),
                }
            }
            for value in values
        ]
    }


def build_method_matcher(
    *,
    class_name: str,
    method_name: str,
    params: list[str] | None = None,
    return_type: str | None = None,
) -> dict[str, Any]:
    matcher: dict[str, Any] = {
        "declaredClass": {
            "className": string_matcher(class_name, "Equals", False),
        },
        "name": string_matcher(method_name, "Equals", False),
    }
    params_matcher = parameter_matchers(params or [])
    if params_matcher is not None:
        matcher["params"] = params_matcher
    if return_type:
        matcher["returnType"] = {
            "className": string_matcher(return_type, "Equals", False),
        }
    return matcher


def build_method_anchor(spec: str) -> dict[str, Any]:
    declared_class, separator, method_name = spec.partition("#")
    if not separator or not declared_class or not method_name:
        raise ValueError(
            f"Method anchor must use ClassName#methodName form: {spec}",
        )
    return build_method_matcher(class_name=declared_class, method_name=method_name)


def build_exact_anchor_matcher(anchor: dict[str, Any]) -> dict[str, Any]:
    if anchor.get("descriptor"):
        parsed = parse_method_descriptor(
            str(anchor["descriptor"]),
            class_name=str(anchor["class_name"]),
            method_name=str(anchor["method_name"]),
        )
        return build_method_matcher(
            class_name=parsed.class_name,
            method_name=parsed.method_name,
            params=list(parsed.params),
            return_type=parsed.return_type,
        )
    return build_method_matcher(
        class_name=str(anchor["class_name"]),
        method_name=str(anchor["method_name"]),
        params=list(anchor.get("params", [])),
        return_type=str(anchor["return_type"]) if anchor.get("return_type") else None,
    )


def build_relation_query(
    *,
    relation_direction: str,
    anchor: dict[str, Any],
    search_packages: list[str] | None = None,
) -> dict[str, Any]:
    key = "invokeMethods" if relation_direction == "callers" else "callerMethods"
    query: dict[str, Any] = {
        "matcher": {
            key: {
                "methods": [build_exact_anchor_matcher(anchor)],
                "matchType": "Contains",
            }
        }
    }
    if search_packages:
        query["searchPackages"] = list(search_packages)
    return query


def build_class_query(args: argparse.Namespace) -> dict[str, Any]:
    matcher: dict[str, Any] = {}
    maybe_add(
        matcher,
        "className",
        string_matcher(args.class_name, args.match_type, args.ignore_case) if args.class_name else None,
    )
    if args.using_string:
        matcher["usingStrings"] = [
            string_matcher(value, args.match_type, args.ignore_case)
            for value in args.using_string
        ]
    query: dict[str, Any] = {}
    maybe_add(query, "searchPackages", args.search_package)
    maybe_add(query, "excludePackages", args.exclude_package)
    if args.ignore_packages_case:
        query["ignorePackagesCase"] = True
    maybe_add(query, "matcher", matcher)
    return query


def build_method_query(args: argparse.Namespace) -> dict[str, Any]:
    matcher: dict[str, Any] = {}
    maybe_add(
        matcher,
        "name",
        string_matcher(args.method_name, args.match_type, args.ignore_case) if args.method_name else None,
    )
    maybe_add(
        matcher,
        "declaredClass",
        class_name_matcher(
            args.declared_class,
            match_type=args.class_match_type,
            ignore_case=args.class_ignore_case,
        ),
    )
    maybe_add(
        matcher,
        "returnType",
        class_name_matcher(
            args.return_type,
            match_type=args.class_match_type,
            ignore_case=args.class_ignore_case,
        ),
    )
    if args.using_string:
        matcher["usingStrings"] = [
            string_matcher(value, args.match_type, args.ignore_case)
            for value in args.using_string
        ]
    if args.using_number:
        matcher["usingNumbers"] = [parse_number_literal(value) for value in args.using_number]
    if args.invoke_method:
        matcher["invokeMethods"] = {
            "methods": [build_method_anchor(value) for value in args.invoke_method],
            "matchType": args.collection_match_type,
        }
    if args.caller_method:
        matcher["callerMethods"] = {
            "methods": [build_method_anchor(value) for value in args.caller_method],
            "matchType": args.collection_match_type,
        }
    query: dict[str, Any] = {}
    maybe_add(query, "searchPackages", args.search_package)
    maybe_add(query, "excludePackages", args.exclude_package)
    if args.ignore_packages_case:
        query["ignorePackagesCase"] = True
    maybe_add(query, "matcher", matcher)
    return query


def build_field_query(args: argparse.Namespace) -> dict[str, Any]:
    matcher: dict[str, Any] = {}
    maybe_add(
        matcher,
        "name",
        string_matcher(args.field_name, args.match_type, args.ignore_case) if args.field_name else None,
    )
    maybe_add(
        matcher,
        "declaredClass",
        class_name_matcher(
            args.declared_class,
            match_type=args.class_match_type,
            ignore_case=args.class_ignore_case,
        ),
    )
    maybe_add(
        matcher,
        "type",
        class_name_matcher(
            args.field_type,
            match_type=args.class_match_type,
            ignore_case=args.class_ignore_case,
        ),
    )
    query: dict[str, Any] = {}
    maybe_add(query, "searchPackages", args.search_package)
    maybe_add(query, "excludePackages", args.exclude_package)
    if args.ignore_packages_case:
        query["ignorePackagesCase"] = True
    maybe_add(query, "matcher", matcher)
    return query


def build_query(kind: str, args: argparse.Namespace) -> dict[str, Any]:
    if kind == "class":
        return build_class_query(args)
    if kind == "method":
        return build_method_query(args)
    if kind == "field":
        return build_field_query(args)
    raise ValueError(f"Unsupported query kind: {kind}")


def dump_query(query: dict[str, Any]) -> str:
    return json.dumps(query, ensure_ascii=False, separators=(",", ":"))


def add_shared_query_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--search-package", action="append", default=[], help="Limit search to package prefixes.")
    parser.add_argument("--exclude-package", action="append", default=[], help="Exclude package prefixes.")
    parser.add_argument("--ignore-packages-case", action="store_true", help="Ignore package case while filtering.")
    parser.add_argument(
        "--match-type",
        choices=STRING_MATCH_TYPES,
        default="Contains",
        help="String match type for name or string filters.",
    )
    parser.add_argument(
        "--ignore-case",
        action=argparse.BooleanOptionalAction,
        default=True,
        help="Case-insensitive string matching for name or string filters.",
    )
    parser.add_argument(
        "--class-match-type",
        choices=STRING_MATCH_TYPES,
        default="Equals",
        help="Match type for class/type names.",
    )
    parser.add_argument(
        "--class-ignore-case",
        action=argparse.BooleanOptionalAction,
        default=False,
        help="Case sensitivity for class/type names.",
    )
    parser.add_argument(
        "--collection-match-type",
        choices=COLLECTION_MATCH_TYPES,
        default="Contains",
        help="Match type for matcher collections such as invokeMethods.",
    )


def create_query_parser(*, add_help: bool = True) -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Build common dexclub-cli query JSON without hand-writing nested matcher objects.",
        add_help=add_help,
    )
    subparsers = parser.add_subparsers(dest="kind", required=True)
    configure_query_subparsers(subparsers)
    return parser


def configure_query_subparsers(
    subparsers,
    *,
    parent_parsers: list[argparse.ArgumentParser] | None = None,
) -> dict[str, argparse.ArgumentParser]:
    parents = list(parent_parsers or [])

    class_parser = subparsers.add_parser("class", help="Build a find-class query.", parents=parents)
    add_shared_query_args(class_parser)
    class_parser.add_argument("--class-name", "--class", dest="class_name", help="Class name matcher.")
    class_parser.add_argument("--using-string", action="append", default=[], help="Known string usage.")
    class_parser.add_argument("--output", help="Optional output file.")

    method_parser = subparsers.add_parser("method", help="Build a find-method query.", parents=parents)
    add_shared_query_args(method_parser)
    method_parser.add_argument("--method-name", help="Method name matcher.")
    method_parser.add_argument("--declared-class", help="Declaring class matcher.")
    method_parser.add_argument("--return-type", help="Return type matcher.")
    method_parser.add_argument("--using-string", action="append", default=[], help="Known string usage.")
    method_parser.add_argument("--using-number", action="append", default=[], help="Known numeric constant.")
    method_parser.add_argument("--invoke-method", action="append", default=[], help="Anchor callee in Class#method form.")
    method_parser.add_argument("--caller-method", action="append", default=[], help="Anchor caller in Class#method form.")
    method_parser.add_argument("--output", help="Optional output file.")

    field_parser = subparsers.add_parser("field", help="Build a find-field query.", parents=parents)
    add_shared_query_args(field_parser)
    field_parser.add_argument("--field-name", help="Field name matcher.")
    field_parser.add_argument("--declared-class", help="Declaring class matcher.")
    field_parser.add_argument("--field-type", help="Field type matcher.")
    field_parser.add_argument("--output", help="Optional output file.")
    return {
        "class": class_parser,
        "method": method_parser,
        "field": field_parser,
    }


def write_query_output(text: str, output_path: str | None) -> None:
    if not output_path:
        print(text)
        return
    output_file = Path(output_path)
    output_file.parent.mkdir(parents=True, exist_ok=True)
    output_file.write_text(text + "\n", encoding="utf-8")
    print(str(output_file.resolve()))

#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

from code_analysis import analyze_code


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Scan exported Java or smali code for strings, numbers, calls, and field access.",
    )
    parser.add_argument("--input", required=True, help="Path to a Java or smali file.")
    parser.add_argument("--method", help="Optional target method name.")
    parser.add_argument(
        "--mode",
        choices=("summary", "strings", "numbers", "calls", "fields", "all"),
        default="summary",
        help="Which slice of the analysis result to print.",
    )
    parser.add_argument(
        "--format",
        choices=("text", "json"),
        default="text",
        help="Output format.",
    )
    return parser.parse_args()


def select_payload(report: dict[str, object], mode: str) -> dict[str, object]:
    if mode == "all":
        return report
    if mode == "summary":
        return {
            "kind": report["kind"],
            "scope": report["scope"],
            "branchLineCount": report["branchLineCount"],
            "returnLineCount": report["returnLineCount"],
            "stringCount": len(report["strings"]),
            "numberCount": len(report["numbers"]),
            "methodCallCount": len(report["methodCalls"]),
            "fieldAccessCount": len(report["fieldAccesses"]),
            "strings": report["strings"],
            "numbers": report["numbers"],
            "methodCalls": report["methodCalls"],
            "fieldAccesses": report["fieldAccesses"],
            "branchHotspots": report["branchHotspots"],
            "structuredSummary": report["structuredSummary"],
            "largeMethodAnalysis": report["largeMethodAnalysis"],
        }
    if mode == "strings":
        return {"scope": report["scope"], "strings": report["strings"]}
    if mode == "numbers":
        return {"scope": report["scope"], "numbers": report["numbers"]}
    if mode == "calls":
        return {"scope": report["scope"], "methodCalls": report["methodCalls"]}
    return {"scope": report["scope"], "fieldAccesses": report["fieldAccesses"]}


def format_text(payload: dict[str, object], mode: str) -> str:
    lines: list[str] = []
    scope = payload.get("scope", {})
    if scope:
        lines.append(f"path={scope.get('path')}")
        if scope.get("method"):
            lines.append(f"method={scope.get('method')}")
        if scope.get("startLine") is not None:
            lines.append(f"lines={scope.get('startLine')}..{scope.get('endLine')}")

    def append_occurrences(title: str, items: list[dict[str, object]]) -> None:
        lines.append(f"{title}={len(items)}")
        for item in items:
            line_text = ",".join(str(number) for number in item["lines"])
            lines.append(f"  {item['value']} | count={item['count']} | lines={line_text}")

    if mode in {"summary", "all"}:
        if "kind" in payload:
            lines.append(f"kind={payload['kind']}")
        if "branchLineCount" in payload:
            lines.append(f"branchLineCount={payload['branchLineCount']}")
        if "returnLineCount" in payload:
            lines.append(f"returnLineCount={payload['returnLineCount']}")
        structured_summary = payload.get("structuredSummary")
        if isinstance(structured_summary, dict):
            lines.append(f"structuredSummaryKind={structured_summary.get('kind')}")
            if structured_summary.get("supported"):
                lines.append(f"basicBlockCount={structured_summary.get('basicBlockCount')}")
                lines.append(f"callClusterCount={structured_summary.get('callClusterCount')}")
                lines.append(f"constantClusterCount={structured_summary.get('constantClusterCount')}")
                lines.append(f"focusSnippetCount={structured_summary.get('focusSnippetCount')}")
        large_method_analysis = payload.get("largeMethodAnalysis")
        if isinstance(large_method_analysis, dict):
            lines.append(f"isLargeMethod={large_method_analysis.get('isLargeMethod')}")
            if large_method_analysis.get("isLargeMethod"):
                lines.append(f"largeMethodThreshold={large_method_analysis.get('lineThreshold')}")
                lines.append(f"largeMethodGroups={large_method_analysis.get('groupCount')}")
    if "strings" in payload:
        append_occurrences("strings", payload["strings"])
    if "numbers" in payload:
        append_occurrences("numbers", payload["numbers"])
    if "methodCalls" in payload:
        append_occurrences("methodCalls", payload["methodCalls"])
    if "fieldAccesses" in payload:
        append_occurrences("fieldAccesses", payload["fieldAccesses"])
    if "branchHotspots" in payload:
        lines.append(f"branchHotspots={len(payload['branchHotspots'])}")
        for item in payload["branchHotspots"]:
            lines.append(f"  line={item['line']} | opcode={item['opcode']} | text={item['text']}")
    if mode == "all" and "code" in payload:
        lines.append("code<<EOF")
        lines.append(str(payload["code"]))
        lines.append("EOF")
    return "\n".join(lines)


def main() -> None:
    args = parse_args()
    input_path = Path(args.input)
    if not input_path.is_file():
        raise SystemExit(f"Input file not found: {input_path.resolve()}")

    report = analyze_code(input_path, args.method)
    payload = select_payload(report, args.mode)

    if args.format == "json":
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        return

    print(format_text(payload, args.mode))


if __name__ == "__main__":
    main()

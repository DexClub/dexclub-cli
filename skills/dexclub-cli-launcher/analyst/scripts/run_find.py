#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

from analyst_storage import allocate_helper_output_dir, ensure_apk_input_cache, ensure_dex_input_cache
from helper_cli import (
    ActionableArgumentParser,
    format_actionable_error,
    require_existing_file,
    summarize_process_failure,
)
from query_builder import build_query, configure_query_subparsers, dump_query

QUERY_KINDS = {"class", "method", "field"}
COMMON_OPTION_SPECS = {
    "--input": 1,
    "--limit": 1,
    "--output-format": 1,
    "--format": 1,
    "--output-file": 1,
    "--print-query": 0,
    "--raw-query-json": 1,
}


class RunFindSubparser(ActionableArgumentParser):
    def __init__(self, *args, **kwargs) -> None:
        kwargs.setdefault("error_summary", "invalid run_find.py arguments")
        kwargs.setdefault(
            "recommended_action",
            (
                "Use `run_find.py <class|method|field> --input <apk-or-dex> ... --output-format json`, "
                "or switch to `analyze.py run` for stable analyst workflows."
            ),
        )
        super().__init__(*args, **kwargs)


def add_helper_runner_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--input", action="append", required=True, help="APK or dex input. Repeat for multiple dex files.")
    parser.add_argument("--limit", type=int, help="Optional result limit.")
    parser.add_argument(
        "--output-format",
        "--format",
        dest="output_format",
        choices=("text", "json"),
        default="json",
        help="dexclub-cli output format.",
    )
    parser.add_argument("--output-file", help="Optional dexclub-cli output file.")
    parser.add_argument("--print-query", action="store_true", help="Print the generated query JSON to stderr before execution.")
    parser.add_argument("--raw-query-json", help="Optional raw query JSON. When set, skip the built-in query builder.")


def create_parser() -> argparse.ArgumentParser:
    parser = ActionableArgumentParser(
        description="Build a common query JSON shape and immediately execute dexclub-cli find-* through the launcher layer.",
        add_help=True,
        formatter_class=argparse.RawTextHelpFormatter,
        epilog=(
            "Preferred ordering matches the real dexclub-cli contract:\n"
            "  run_find.py method --input ./inputs/classes.dex --limit 20 --using-string login --output-format json\n"
            "\n"
            "Legacy compatibility is still accepted:\n"
            "  run_find.py --input ./inputs/classes.dex --limit 20 method --using-string login --output-format json\n"
            "\n"
            "For common analyst workflows, prefer the stable entry:\n"
            "  analyze.py run --task-type <task> --input-json '<payload>'"
        ),
        error_summary="invalid run_find.py arguments",
        recommended_action=(
            "Use `run_find.py <class|method|field> --input <apk-or-dex> ... --output-format json`, "
            "or switch to `analyze.py run` for stable analyst workflows."
        ),
    )
    subparsers = parser.add_subparsers(dest="kind", required=True, parser_class=RunFindSubparser)
    helper_parent = argparse.ArgumentParser(add_help=False)
    add_helper_runner_args(helper_parent)
    configure_query_subparsers(subparsers, parent_parsers=[helper_parent])
    return parser


def build_launcher_command(skill_root: Path, cli_args: list[str]) -> list[str]:
    launcher_scripts = skill_root / "launcher" / "scripts"
    if os.name == "nt":
        bat_path = launcher_scripts / "run_latest_release.bat"
        return ["cmd.exe", "/c", str(bat_path.resolve()), "--", *cli_args]
    sh_path = launcher_scripts / "run_latest_release.sh"
    return ["bash", str(sh_path.resolve()), "--", *cli_args]


def relay_stream(content: str, *, target) -> None:
    if content:
        print(content, end="" if content.endswith("\n") else "\n", file=target)


def normalize_legacy_argv(argv: list[str]) -> list[str]:
    kind_index = next((index for index, token in enumerate(argv) if token in QUERY_KINDS), None)
    if kind_index is None or kind_index == 0:
        return argv
    prefix = argv[:kind_index]
    kind = argv[kind_index]
    suffix = argv[kind_index + 1:]
    reordered_prefix: list[str] = []
    index = 0
    while index < len(prefix):
        token = prefix[index]
        if token not in COMMON_OPTION_SPECS:
            return argv
        reordered_prefix.append(token)
        value_count = COMMON_OPTION_SPECS[token]
        for _ in range(value_count):
            index += 1
            if index >= len(prefix):
                return argv
            reordered_prefix.append(prefix[index])
        index += 1
    return [kind, *reordered_prefix, *suffix]


def main() -> None:
    parser = create_parser()
    args = parser.parse_args(normalize_legacy_argv(sys.argv[1:]))

    if args.raw_query_json:
        try:
            query = json.loads(args.raw_query_json)
        except json.JSONDecodeError as exc:
            raise SystemExit(
                format_actionable_error(
                    summary="invalid run_find.py query input",
                    cause=f"`--raw-query-json` is not valid JSON: {exc.msg}",
                    recommended_action=(
                        "Pass one valid JSON object string to `--raw-query-json`, "
                        "or remove it and use the built-in `class|method|field` query flags."
                    ),
                )
            ) from exc
    else:
        query = build_query(args.kind, args)
    query_json = dump_query(query)
    if args.print_query:
        print(query_json, file=subprocess.sys.stderr)

    command_name = f"find-{args.kind}"
    cli_args = [command_name]
    managed_output_dir: Path | None = None
    for input_value in args.input:
        input_path = require_existing_file(
            input_value,
            field_label="input path",
            recommended_action=(
                "Provide an existing `.apk` or `.dex` file. For APK-backed end-to-end tasks, "
                "prefer `analyze.py run` so the planner can choose the helper sequence."
            ),
            allowed_suffixes=(".apk", ".dex"),
        )
        if input_path.suffix.lower() == ".dex":
            ensure_dex_input_cache(input_path)
        else:
            ensure_apk_input_cache(input_path, ensure_extracted_dex=False)
        cli_args.extend(["--input", str(input_path)])
    cli_args.extend(["--query-json", query_json, "--output-format", args.output_format])
    if args.limit is not None:
        cli_args.extend(["--limit", str(args.limit)])
    output_file_path: Path | None = None
    if args.output_format == "json":
        if args.output_file:
            output_file_path = Path(args.output_file).expanduser().resolve()
        else:
            managed_output_dir = allocate_helper_output_dir("run-find-")
            output_file_path = (managed_output_dir / "result.json").resolve()
        cli_args.extend(["--output-file", str(output_file_path)])
    elif args.output_file:
        output_file_path = Path(args.output_file).expanduser().resolve()
        cli_args.extend(["--output-file", str(output_file_path)])

    skill_root = Path(__file__).resolve().parents[2]
    command = build_launcher_command(skill_root, cli_args)
    try:
        completed = subprocess.run(command, capture_output=True, text=True)
        if completed.returncode != 0:
            raise SystemExit(
                format_actionable_error(
                    summary="run_find.py execution failed",
                    cause=summarize_process_failure(
                        completed,
                        fallback=f"dexclub-cli exited with code {completed.returncode}.",
                    ),
                    recommended_action=(
                        "Check whether the input file is valid for the selected query and narrow the matcher. "
                        "For method-logic or call-graph tasks, prefer `analyze.py run`."
                    ),
                    notes=[
                        f"Launcher exit code: {completed.returncode}",
                        "See the launcher stderr/stdout above if you need the raw DexKit details.",
                    ],
                )
            )

        relay_stream(completed.stderr, target=sys.stderr)
        if args.output_format == "json":
            if output_file_path is None or not output_file_path.is_file():
                raise SystemExit(
                    format_actionable_error(
                        summary="run_find.py JSON output is incomplete",
                        cause="dexclub-cli finished without producing the requested JSON output file.",
                        recommended_action=(
                            "Retry with `--output-format json`. If the problem persists, run the launcher command "
                            "directly to inspect raw output."
                        ),
                    )
                )
            payload_text = output_file_path.read_text(encoding="utf-8")
            try:
                json.loads(payload_text)
            except json.JSONDecodeError as exc:
                raise SystemExit(
                    format_actionable_error(
                        summary="run_find.py JSON output is invalid",
                        cause=f"dexclub-cli wrote malformed JSON: {exc.msg}",
                        recommended_action=(
                            "Re-run with the same command and inspect the generated `--output-file`, "
                            "or fall back to the launcher directly to isolate the failing query."
                        ),
                    )
                ) from exc
            if completed.stdout.strip():
                relay_stream(completed.stdout, target=sys.stderr)
            relay_stream(payload_text, target=sys.stdout)
            return

        relay_stream(completed.stdout, target=sys.stdout)
    finally:
        if managed_output_dir is not None:
            shutil.rmtree(managed_output_dir, ignore_errors=True)


if __name__ == "__main__":
    main()

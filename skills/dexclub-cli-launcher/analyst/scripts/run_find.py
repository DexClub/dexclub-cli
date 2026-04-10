#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import subprocess
import sys
import shutil
from pathlib import Path

from analyst_storage import allocate_helper_output_dir, ensure_apk_input_cache, ensure_dex_input_cache
from query_builder import build_query, create_query_parser, dump_query


def create_parser() -> argparse.ArgumentParser:
    base_parser = create_query_parser(add_help=False)
    parser = argparse.ArgumentParser(
        description="Build a common query JSON shape and immediately execute dexclub-cli find-* through the launcher layer.",
        parents=[base_parser],
        add_help=True,
    )
    parser.add_argument("--input", action="append", required=True, help="APK or dex input. Repeat for multiple dex files.")
    parser.add_argument("--limit", type=int, help="Optional result limit.")
    parser.add_argument("--output-format", choices=("text", "json"), default="json", help="dexclub-cli output format.")
    parser.add_argument("--output-file", help="Optional dexclub-cli output file.")
    parser.add_argument("--print-query", action="store_true", help="Print the generated query JSON to stderr before execution.")
    parser.add_argument("--raw-query-json", help="Optional raw query JSON. When set, skip the built-in query builder.")
    return parser


def build_launcher_command(skill_root: Path, cli_args: list[str]) -> list[str]:
    launcher_scripts = skill_root / "launcher" / "scripts"
    if launcher_scripts.joinpath("run_latest_release.sh").exists():
        return ["bash", str((launcher_scripts / "run_latest_release.sh").resolve()), "--", *cli_args]
    return ["cmd.exe", "/c", str((launcher_scripts / "run_latest_release.bat").resolve()), "--", *cli_args]


def relay_stream(content: str, *, target) -> None:
    if content:
        print(content, end="" if content.endswith("\n") else "\n", file=target)


def main() -> None:
    parser = create_parser()
    args = parser.parse_args()

    if args.raw_query_json:
        try:
            query = json.loads(args.raw_query_json)
        except json.JSONDecodeError as exc:
            raise SystemExit(f"Invalid --raw-query-json: {exc.msg}") from exc
    else:
        query = build_query(args.kind, args)
    query_json = dump_query(query)
    if args.print_query:
        print(query_json, file=subprocess.sys.stderr)

    command_name = f"find-{args.kind}"
    cli_args = [command_name]
    managed_output_dir: Path | None = None
    for input_value in args.input:
        input_path = Path(input_value).expanduser().resolve()
        if input_path.suffix.lower() == ".dex":
            ensure_dex_input_cache(input_path)
        else:
            if input_path.suffix.lower() == ".apk":
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
            relay_stream(completed.stdout, target=sys.stderr)
            relay_stream(completed.stderr, target=sys.stderr)
            raise SystemExit(completed.returncode)

        relay_stream(completed.stderr, target=sys.stderr)
        if args.output_format == "json":
            if output_file_path is None or not output_file_path.is_file():
                relay_stream(completed.stdout, target=sys.stderr)
                raise SystemExit("dexclub-cli did not produce the requested JSON output file.")
            payload_text = output_file_path.read_text(encoding="utf-8")
            try:
                json.loads(payload_text)
            except json.JSONDecodeError as exc:
                raise SystemExit(f"Invalid JSON output from dexclub-cli: {exc.msg}") from exc
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

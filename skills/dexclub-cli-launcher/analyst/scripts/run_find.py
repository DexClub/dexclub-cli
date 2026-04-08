#!/usr/bin/env python3
from __future__ import annotations

import argparse
import subprocess
from pathlib import Path

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
    return parser


def build_launcher_command(skill_root: Path, cli_args: list[str]) -> list[str]:
    launcher_scripts = skill_root / "launcher" / "scripts"
    if launcher_scripts.joinpath("run_latest_release.sh").exists():
        return ["bash", str((launcher_scripts / "run_latest_release.sh").resolve()), "--", *cli_args]
    return ["cmd.exe", "/c", str((launcher_scripts / "run_latest_release.bat").resolve()), "--", *cli_args]


def main() -> None:
    parser = create_parser()
    args = parser.parse_args()

    query = build_query(args.kind, args)
    query_json = dump_query(query)
    if args.print_query:
        print(query_json, file=subprocess.sys.stderr)

    command_name = f"find-{args.kind}"
    cli_args = [command_name]
    for input_value in args.input:
        cli_args.extend(["--input", str(Path(input_value).resolve())])
    cli_args.extend(["--query-json", query_json, "--output-format", args.output_format])
    if args.limit is not None:
        cli_args.extend(["--limit", str(args.limit)])
    if args.output_file:
        cli_args.extend(["--output-file", str(Path(args.output_file).resolve())])

    skill_root = Path(__file__).resolve().parents[2]
    command = build_launcher_command(skill_root, cli_args)
    subprocess.run(command, check=True)


if __name__ == "__main__":
    main()

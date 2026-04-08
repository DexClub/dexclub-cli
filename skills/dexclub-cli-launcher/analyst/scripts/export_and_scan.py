#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import subprocess
import tempfile
from pathlib import Path

from code_analysis import analyze_code
from scan_exported_code import format_text, select_payload


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Export a target class through the launcher layer, then scan the exported code.",
    )
    parser.add_argument("--input-dex", required=True, help="Path to a single dex file.")
    parser.add_argument("--class", dest="class_name", required=True, help="Target class name.")
    parser.add_argument("--method", help="Optional target method name for scoped scanning.")
    parser.add_argument(
        "--language",
        choices=("java", "smali"),
        default="java",
        help="Export format to request from dexclub-cli.",
    )
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
    parser.add_argument(
        "--output-dir",
        help="Optional output directory. When omitted, a temporary directory is created and kept.",
    )
    return parser.parse_args()


def build_launcher_command(skill_root: Path, cli_args: list[str]) -> list[str]:
    launcher_scripts = skill_root / "launcher" / "scripts"
    if os.name == "nt":
        bat_path = launcher_scripts / "run_latest_release.bat"
        return ["cmd.exe", "/c", str(bat_path), "--", *cli_args]
    sh_path = launcher_scripts / "run_latest_release.sh"
    return ["bash", str(sh_path), "--", *cli_args]


def main() -> None:
    args = parse_args()
    dex_path = Path(args.input_dex)
    if not dex_path.is_file():
        raise SystemExit(f"Input dex not found: {dex_path.resolve()}")

    output_dir = Path(args.output_dir) if args.output_dir else Path(tempfile.mkdtemp(prefix="dexclub-analyst-"))
    output_dir.mkdir(parents=True, exist_ok=True)

    suffix = ".java" if args.language == "java" else ".smali"
    file_name = args.class_name.replace(".", "_") + suffix
    export_path = output_dir / file_name

    skill_root = Path(__file__).resolve().parents[2]
    export_command = build_launcher_command(
        skill_root,
        [
            f"export-{args.language}",
            "--input",
            str(dex_path.resolve()),
            "--class",
            args.class_name,
            "--output",
            str(export_path.resolve()),
        ],
    )
    subprocess.run(export_command, check=True)

    report = analyze_code(export_path, args.method)
    payload = select_payload(report, args.mode)
    payload["exportPath"] = str(export_path.resolve())

    if args.format == "json":
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        return
    print(format_text(payload, args.mode))
    print(f"exportPath={export_path.resolve()}")


if __name__ == "__main__":
    main()

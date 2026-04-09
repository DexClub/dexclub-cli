#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path

from analyst_storage import allocate_helper_output_dir, ensure_dex_input_cache, inputs_cache_root
from code_analysis import analyze_code
from process_exec import run_captured_process
from scan_exported_code import format_text, select_payload


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Export a target class through the launcher layer, then scan the exported code.",
    )
    parser.add_argument("--input-dex", required=True, help="Path to a single dex file.")
    parser.add_argument("--class", dest="class_name", required=True, help="Target class name.")
    parser.add_argument("--method", help="Optional target method name for scoped scanning.")
    parser.add_argument("--method-descriptor", help="Optional exact method descriptor for precise smali scoping.")
    parser.add_argument(
        "--language",
        choices=("java", "smali"),
        default="smali",
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
        help="Optional output directory. When omitted, a directory is created under the analyst work root.",
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
    dex_path = Path(args.input_dex).expanduser().resolve()
    if not dex_path.is_file():
        raise SystemExit(f"Input dex not found: {dex_path.resolve()}")
    dex_cache_ref = ensure_dex_input_cache(dex_path)
    export_input_dex = (dex_cache_ref.cached_path or dex_path).resolve()

    created_output_dir = not bool(args.output_dir)
    output_dir = (
        Path(args.output_dir).expanduser().resolve()
        if args.output_dir
        else allocate_helper_output_dir("export-and-scan-")
    )
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
            str(export_input_dex),
            "--class",
            args.class_name,
            "--output",
            str(export_path.resolve()),
        ],
    )
    export_logs_root = output_dir / ".export-and-scan-logs" / "launcher_export"
    execution = run_captured_process(
        step_id="launcher_export",
        command=export_command,
        artifact_dir=export_logs_root,
        payload_kind="none",
    )
    if execution.status != "ok":
        message = execution.diagnostics.get("cause") or execution.diagnostics.get("message") or "Export failed."
        raise SystemExit(str(message))

    report = analyze_code(
        export_path,
        method_name=args.method,
        method_descriptor=args.method_descriptor,
    )
    payload = select_payload(report, args.mode)
    payload["artifactRoot"] = str(output_dir.resolve())
    payload["cacheRoot"] = str(inputs_cache_root().resolve())
    payload["temporaryPaths"] = [str(output_dir.resolve())] if created_output_dir else []
    payload["exportPath"] = str(export_path.resolve())
    if args.method_descriptor:
        payload["methodDescriptor"] = args.method_descriptor

    if args.format == "json":
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        return
    print(format_text(payload, args.mode))
    print(f"artifactRoot={output_dir.resolve()}")
    print(f"exportPath={export_path.resolve()}")


if __name__ == "__main__":
    main()

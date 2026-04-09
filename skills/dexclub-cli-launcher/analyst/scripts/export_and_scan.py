#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import uuid
from pathlib import Path

from analyst_storage import (
    allocate_helper_output_dir,
    ensure_dex_input_cache,
    export_and_scan_cache_root,
    inputs_cache_root,
    sha256_file,
    utc_now_iso,
    write_json,
)
from code_analysis import analyze_code
from process_exec import run_captured_process
from scan_exported_code import format_text, select_payload

CACHE_SCHEMA_VERSION = "v1"


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


def stable_hash(payload: dict[str, object]) -> str:
    encoded = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def build_cache_key(*, class_name: str, language: str, method_name: str | None, method_descriptor: str | None) -> str:
    return stable_hash(
        {
            "schema_version": CACHE_SCHEMA_VERSION,
            "class_name": class_name,
            "language": language,
            "method_name": method_name or "",
            "method_descriptor": method_descriptor or "",
        }
    )


def load_cached_report(path: Path) -> dict[str, object] | None:
    if not path.is_file():
        return None
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None
    return payload if isinstance(payload, dict) else None


def is_valid_cache_dir(*, cache_dir: Path, export_path: Path, report_path: Path) -> bool:
    return cache_dir.is_dir() and export_path.is_file() and load_cached_report(report_path) is not None


def materialize_export(*, cached_export_path: Path, output_dir: Path, file_name: str) -> Path:
    target_path = (output_dir / file_name).resolve()
    if target_path != cached_export_path.resolve():
        target_path.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(cached_export_path, target_path)
    return target_path


def finalize_payload(
    *,
    report: dict[str, object],
    mode: str,
    output_dir: Path,
    created_output_dir: bool,
    export_path: Path,
    cache_dir: Path,
    cache_key: str,
    cache_hit: bool,
    method_descriptor: str | None,
) -> dict[str, object]:
    payload = select_payload(report, mode)
    payload["artifactRoot"] = str(output_dir.resolve())
    payload["cacheRoot"] = str(inputs_cache_root().resolve())
    payload["exportScanCacheRoot"] = str(export_and_scan_cache_root().resolve())
    payload["cachePath"] = str(cache_dir.resolve())
    payload["cacheKey"] = cache_key
    payload["cacheHit"] = cache_hit
    payload["temporaryPaths"] = [str(output_dir.resolve())] if created_output_dir else []
    payload["exportPath"] = str(export_path.resolve())
    if method_descriptor:
        payload["methodDescriptor"] = method_descriptor
    return payload


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
    export_path = (output_dir / file_name).resolve()

    dex_digest = sha256_file(export_input_dex)
    cache_key = build_cache_key(
        class_name=args.class_name,
        language=args.language,
        method_name=args.method,
        method_descriptor=args.method_descriptor,
    )
    cache_dir = (export_and_scan_cache_root() / dex_digest / cache_key).resolve()
    cached_export_path = (cache_dir / file_name).resolve()
    cached_report_path = (cache_dir / "analysis-report.json").resolve()
    cached_report = load_cached_report(cached_report_path) if is_valid_cache_dir(
        cache_dir=cache_dir,
        export_path=cached_export_path,
        report_path=cached_report_path,
    ) else None

    if cached_report is None and cache_dir.exists():
        shutil.rmtree(cache_dir, ignore_errors=True)

    skill_root = Path(__file__).resolve().parents[2]
    cache_hit = cached_report is not None
    if cached_report is None:
        cache_dir.parent.mkdir(parents=True, exist_ok=True)
        tmp_cache_dir = cache_dir.parent / f".{cache_key}.tmp-{uuid.uuid4().hex[:8]}"
        shutil.rmtree(tmp_cache_dir, ignore_errors=True)
        tmp_cache_dir.mkdir(parents=True, exist_ok=False)
        try:
            tmp_export_path = (tmp_cache_dir / file_name).resolve()
            export_command = build_launcher_command(
                skill_root,
                [
                    f"export-{args.language}",
                    "--input",
                    str(export_input_dex),
                    "--class",
                    args.class_name,
                    "--output",
                    str(tmp_export_path),
                ],
            )
            export_logs_root = tmp_cache_dir / ".export-and-scan-logs" / "launcher_export"
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
                tmp_export_path,
                method_name=args.method,
                method_descriptor=args.method_descriptor,
            )
            write_json(tmp_cache_dir / "analysis-report.json", report)
            write_json(
                tmp_cache_dir / "cache-meta.json",
                {
                    "schema_version": CACHE_SCHEMA_VERSION,
                    "created_at": utc_now_iso(),
                    "dex_sha256": dex_digest,
                    "input_dex": str(dex_path),
                    "cached_input_dex": str(export_input_dex),
                    "class_name": args.class_name,
                    "language": args.language,
                    "method_name": args.method,
                    "method_descriptor": args.method_descriptor,
                },
            )
            try:
                tmp_cache_dir.replace(cache_dir)
            except FileExistsError:
                shutil.rmtree(tmp_cache_dir, ignore_errors=True)
        finally:
            shutil.rmtree(tmp_cache_dir, ignore_errors=True)
        cached_report = load_cached_report(cached_report_path)
        if cached_report is None or not cached_export_path.is_file():
            raise SystemExit("Export cache write failed.")

    export_path = materialize_export(
        cached_export_path=cached_export_path,
        output_dir=output_dir,
        file_name=file_name,
    )
    payload = finalize_payload(
        report=cached_report,
        mode=args.mode,
        output_dir=output_dir,
        created_output_dir=created_output_dir,
        export_path=export_path,
        cache_dir=cache_dir,
        cache_key=cache_key,
        cache_hit=cache_hit,
        method_descriptor=args.method_descriptor,
    )

    if args.format == "json":
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        return
    print(format_text(payload, args.mode))
    print(f"artifactRoot={output_dir.resolve()}")
    print(f"exportPath={export_path.resolve()}")


if __name__ == "__main__":
    main()

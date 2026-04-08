#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import subprocess
import sys
import zipfile
from pathlib import Path

from analyst_storage import ensure_apk_input_cache


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Extract classes*.dex from an APK and resolve the dex containing a target class.",
    )
    parser.add_argument("--input-apk", required=True, help="Path to a single APK file.")
    parser.add_argument("--class", dest="class_name", required=True, help="Target class name.")
    parser.add_argument(
        "--format",
        choices=("text", "json"),
        default="json",
        help="Output format.",
    )
    parser.add_argument(
        "--output-dir",
        help="Optional explicit output directory for extracted dex files.",
    )
    return parser.parse_args()


def dex_entry_sort_key(name: str) -> tuple[int, str]:
    if name == "classes.dex":
        return (1, name)
    suffix = name.removeprefix("classes").removesuffix(".dex")
    if suffix.isdigit():
        return (int(suffix), name)
    return (10**9, name)


def extract_json_payload(stdout: str) -> object:
    lines = stdout.splitlines()
    for start in range(len(lines)):
        candidate = "\n".join(lines[start:]).strip()
        if not candidate or candidate[0] not in "[{":
            continue
        try:
            return json.loads(candidate)
        except json.JSONDecodeError:
            continue
    stripped = stdout.strip()
    if stripped and stripped[0] in "[{":
        return json.loads(stripped)
    raise ValueError("Unable to locate a JSON payload in stdout.")


def run_class_lookup(dex_path: Path, class_name: str) -> bool:
    run_find_path = Path(__file__).resolve().parent / "run_find.py"
    command = [
        sys.executable,
        str(run_find_path),
        "--input",
        str(dex_path.resolve()),
        "--output-format",
        "json",
        "--limit",
        "1",
        "class",
        "--class-name",
        class_name,
        "--match-type",
        "Equals",
        "--no-ignore-case",
    ]
    completed = subprocess.run(command, capture_output=True, text=True, check=True)
    payload = extract_json_payload(completed.stdout)
    return isinstance(payload, list) and len(payload) > 0


def format_text(payload: dict[str, object]) -> str:
    lines = [
        f"apk_path={payload['apk_path']}",
        f"class_name={payload['class_name']}",
        f"candidate_count={len(payload['candidate_dex_paths'])}",
    ]
    if payload.get("resolved_dex_path"):
        lines.append(f"resolved_dex_path={payload['resolved_dex_path']}")
    for path in payload["candidate_dex_paths"]:
        lines.append(f"candidate_dex_path={path}")
    return "\n".join(lines)


def main() -> None:
    args = parse_args()
    apk_path = Path(args.input_apk).expanduser().resolve()
    if not apk_path.is_file():
        raise SystemExit(f"Input APK not found: {apk_path.resolve()}")

    if args.output_dir:
        output_dir = Path(args.output_dir).expanduser().resolve()
    else:
        cache_ref = ensure_apk_input_cache(apk_path, ensure_extracted_dex=True)
        if cache_ref.extracted_dex_dir is None:
            raise SystemExit("Failed to build extracted dex cache for the input APK.")
        output_dir = cache_ref.extracted_dex_dir.resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    dex_entries: list[str] = []
    with zipfile.ZipFile(apk_path) as apk_zip:
        for info in apk_zip.infolist():
            if info.filename == "classes.dex" or (
                info.filename.startswith("classes")
                and info.filename.endswith(".dex")
            ):
                dex_entries.append(info.filename)
        dex_entries.sort(key=dex_entry_sort_key)

        extracted_dex_paths: list[str] = []
        candidate_dex_paths: list[str] = []
        extracted_ready = all((output_dir / entry_name).is_file() for entry_name in dex_entries)
        for entry_name in dex_entries:
            extracted_path = output_dir / entry_name
            if not extracted_ready:
                extracted_path.parent.mkdir(parents=True, exist_ok=True)
                with apk_zip.open(entry_name) as src, extracted_path.open("wb") as dst:
                    dst.write(src.read())
            extracted_dex_paths.append(str(extracted_path.resolve()))
            if run_class_lookup(extracted_path, args.class_name):
                candidate_dex_paths.append(str(extracted_path.resolve()))

    payload = {
        "apk_path": str(apk_path),
        "class_name": args.class_name,
        "output_dir": str(output_dir.resolve()),
        "candidate_dex_paths": candidate_dex_paths,
        "extracted_dex_paths": extracted_dex_paths,
        "resolved_dex_path": candidate_dex_paths[0] if len(candidate_dex_paths) == 1 else None,
    }

    if args.format == "json":
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        return
    print(format_text(payload))


if __name__ == "__main__":
    main()

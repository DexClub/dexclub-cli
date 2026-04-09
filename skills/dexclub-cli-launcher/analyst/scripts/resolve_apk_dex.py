#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
import zipfile
from pathlib import Path

from analyst_storage import ensure_apk_input_cache, inputs_cache_root, utc_now_iso, write_json
from process_exec import run_captured_process

INDEX_FILE_NAME = "class-dex-index-v1.json"
INDEX_SCHEMA_VERSION = "v1"


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


def sort_dex_entries(names: list[str]) -> list[str]:
    return sorted(names, key=dex_entry_sort_key)


class FullIndexUnavailable(RuntimeError):
    pass


def run_class_lookup(dex_path: Path, class_name: str, *, artifact_dir: Path) -> bool:
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
    result = run_captured_process(
        step_id=f"class_lookup_{dex_path.stem}",
        command=command,
        artifact_dir=artifact_dir,
        payload_kind="json",
    )
    if result.status != "ok":
        raise RuntimeError(f"class lookup failed for {dex_path.name}: {result.diagnostics.get('cause')}")
    payload = result.payload
    return isinstance(payload, list) and len(payload) > 0


def run_class_inventory(dex_path: Path, *, artifact_dir: Path) -> set[str]:
    run_find_path = Path(__file__).resolve().parent / "run_find.py"
    command = [
        sys.executable,
        str(run_find_path),
        "--input",
        str(dex_path.resolve()),
        "--output-format",
        "json",
        "--raw-query-json",
        "{}",
        "class",
    ]
    result = run_captured_process(
        step_id=f"class_inventory_{dex_path.stem}",
        command=command,
        artifact_dir=artifact_dir,
        payload_kind="json",
    )
    if result.status != "ok":
        raise FullIndexUnavailable(
            f"class inventory failed for {dex_path.name}: {result.diagnostics.get('cause')}"
        )
    payload = result.payload
    if not isinstance(payload, list):
        raise FullIndexUnavailable(f"class inventory returned a non-list payload for {dex_path.name}")
    class_names = {
        str(item["name"])
        for item in payload
        if isinstance(item, dict) and isinstance(item.get("name"), str)
    }
    return class_names


def ensure_extracted_dex_files(
    *,
    apk_path: Path,
    output_dir: Path,
    dex_entries: list[str],
) -> list[Path]:
    extracted_ready = all((output_dir / entry_name).is_file() for entry_name in dex_entries)
    if not extracted_ready:
        with zipfile.ZipFile(apk_path) as apk_zip:
            for entry_name in dex_entries:
                extracted_path = output_dir / entry_name
                extracted_path.parent.mkdir(parents=True, exist_ok=True)
                with apk_zip.open(entry_name) as src, extracted_path.open("wb") as dst:
                    dst.write(src.read())
    return [(output_dir / entry_name).resolve() for entry_name in dex_entries]


def index_cache_path(cache_dir: Path) -> Path:
    return cache_dir / INDEX_FILE_NAME


def load_index_cache(cache_dir: Path) -> dict[str, object]:
    path = index_cache_path(cache_dir)
    if not path.is_file():
        return {
            "schema_version": INDEX_SCHEMA_VERSION,
            "index_state": "unknown",
            "updated_at": None,
            "class_to_dex_entries": {},
        }
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {
            "schema_version": INDEX_SCHEMA_VERSION,
            "index_state": "unknown",
            "updated_at": None,
            "class_to_dex_entries": {},
        }
    if not isinstance(payload, dict):
        return {
            "schema_version": INDEX_SCHEMA_VERSION,
            "index_state": "unknown",
            "updated_at": None,
            "class_to_dex_entries": {},
        }
    raw_map = payload.get("class_to_dex_entries", {})
    normalized_map: dict[str, list[str]] = {}
    if isinstance(raw_map, dict):
        for class_name, entry_names in raw_map.items():
            if not isinstance(class_name, str) or not isinstance(entry_names, list):
                continue
            normalized_map[class_name] = sort_dex_entries(
                [str(entry_name) for entry_name in entry_names if isinstance(entry_name, str)]
            )
    return {
        "schema_version": INDEX_SCHEMA_VERSION,
        "index_state": payload.get("index_state", "unknown"),
        "updated_at": payload.get("updated_at"),
        "class_to_dex_entries": normalized_map,
    }


def save_index_cache(cache_dir: Path, payload: dict[str, object]) -> None:
    write_json(index_cache_path(cache_dir), payload)


def build_full_apk_index(
    *,
    cache_dir: Path,
    dex_entries: list[str],
    extracted_dex_paths: list[Path],
    lookup_logs_root: Path,
) -> tuple[dict[str, list[str]], int]:
    class_to_dex_entries: dict[str, list[str]] = {}
    scanned_dex_count = 0
    for entry_name, extracted_path in zip(dex_entries, extracted_dex_paths):
        class_names = run_class_inventory(
            extracted_path,
            artifact_dir=lookup_logs_root / "full-index" / extracted_path.stem,
        )
        scanned_dex_count += 1
        for class_name in class_names:
            class_to_dex_entries.setdefault(class_name, []).append(entry_name)
    normalized_map = {
        class_name: sort_dex_entries(entry_names)
        for class_name, entry_names in class_to_dex_entries.items()
    }
    if not normalized_map:
        raise FullIndexUnavailable("class inventory returned an empty mapping for the APK dex set")
    save_index_cache(
        cache_dir,
        {
            "schema_version": INDEX_SCHEMA_VERSION,
            "index_state": "ready",
            "updated_at": utc_now_iso(),
            "class_to_dex_entries": normalized_map,
        },
    )
    return normalized_map, scanned_dex_count


def record_direct_lookup_cache(
    *,
    cache_dir: Path,
    index_cache: dict[str, object],
    class_name: str,
    candidate_entry_names: list[str],
) -> None:
    class_to_dex_entries = dict(index_cache.get("class_to_dex_entries", {}))
    class_to_dex_entries[class_name] = sort_dex_entries(candidate_entry_names)
    save_index_cache(
        cache_dir,
        {
            "schema_version": INDEX_SCHEMA_VERSION,
            "index_state": index_cache.get("index_state", "unsupported"),
            "updated_at": utc_now_iso(),
            "class_to_dex_entries": class_to_dex_entries,
        },
    )


def format_text(payload: dict[str, object]) -> str:
    lines = [
        f"apk_path={payload['apk_path']}",
        f"class_name={payload['class_name']}",
        f"candidate_count={len(payload['candidate_dex_paths'])}",
        f"cache_hit={payload.get('cache_hit', False)}",
        f"lookup_strategy={payload.get('lookup_strategy')}",
        f"scanned_dex_count={payload.get('scanned_dex_count', 0)}",
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

    cache_ref = ensure_apk_input_cache(apk_path, ensure_extracted_dex=True)
    if cache_ref.extracted_dex_dir is None:
        raise SystemExit("Failed to build extracted dex cache for the input APK.")
    cache_dir = cache_ref.cache_dir.resolve()

    if args.output_dir:
        output_dir = Path(args.output_dir).expanduser().resolve()
    else:
        output_dir = cache_ref.extracted_dex_dir.resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    lookup_logs_root = output_dir / ".resolve-apk-dex-logs"

    dex_entries: list[str] = []
    with zipfile.ZipFile(apk_path) as apk_zip:
        for info in apk_zip.infolist():
            if info.filename == "classes.dex" or (
                info.filename.startswith("classes")
                and info.filename.endswith(".dex")
            ):
                dex_entries.append(info.filename)
        dex_entries = sort_dex_entries(dex_entries)

    extracted_dex_files = ensure_extracted_dex_files(
        apk_path=apk_path,
        output_dir=output_dir,
        dex_entries=dex_entries,
    )
    extracted_dex_paths = [str(path.resolve()) for path in extracted_dex_files]

    cache_hit = False
    lookup_strategy = "direct_lookup_scan"
    scanned_dex_count = 0
    index_cache = load_index_cache(cache_dir)
    class_to_dex_entries = dict(index_cache.get("class_to_dex_entries", {}))
    index_state = str(index_cache.get("index_state", "unknown"))
    candidate_entry_names: list[str] | None = None

    if args.class_name in class_to_dex_entries:
        cache_hit = True
        lookup_strategy = "class_lookup_cache"
        candidate_entry_names = sort_dex_entries(class_to_dex_entries[args.class_name])
    elif index_state == "ready":
        cache_hit = True
        lookup_strategy = "class_index"
        candidate_entry_names = []
    else:
        if index_state != "unsupported":
            try:
                class_to_dex_entries, scanned_dex_count = build_full_apk_index(
                    cache_dir=cache_dir,
                    dex_entries=dex_entries,
                    extracted_dex_paths=extracted_dex_files,
                    lookup_logs_root=lookup_logs_root,
                )
                index_state = "ready"
                lookup_strategy = "class_index"
                candidate_entry_names = sort_dex_entries(class_to_dex_entries.get(args.class_name, []))
            except FullIndexUnavailable:
                index_state = "unsupported"
                save_index_cache(
                    cache_dir,
                    {
                        "schema_version": INDEX_SCHEMA_VERSION,
                        "index_state": "unsupported",
                        "updated_at": utc_now_iso(),
                        "class_to_dex_entries": class_to_dex_entries,
                    },
                )

        if candidate_entry_names is None:
            candidate_entry_names = []
            lookup_strategy = "direct_lookup_scan"
            for extracted_path in extracted_dex_files:
                scanned_dex_count += 1
                if run_class_lookup(
                    extracted_path,
                    args.class_name,
                    artifact_dir=lookup_logs_root / extracted_path.stem,
                ):
                    candidate_entry_names.append(extracted_path.name)
            record_direct_lookup_cache(
                cache_dir=cache_dir,
                index_cache={
                    "index_state": index_state,
                    "class_to_dex_entries": class_to_dex_entries,
                },
                class_name=args.class_name,
                candidate_entry_names=candidate_entry_names,
            )

    candidate_dex_paths = [str((output_dir / entry_name).resolve()) for entry_name in candidate_entry_names]

    payload = {
        "apk_path": str(apk_path),
        "class_name": args.class_name,
        "artifact_root": str(output_dir.resolve()),
        "cache_root": str(inputs_cache_root().resolve()),
        "temporary_paths": [],
        "output_dir": str(output_dir.resolve()),
        "index_cache_path": str(index_cache_path(cache_dir).resolve()),
        "cache_hit": cache_hit,
        "lookup_strategy": lookup_strategy,
        "scanned_dex_count": scanned_dex_count,
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

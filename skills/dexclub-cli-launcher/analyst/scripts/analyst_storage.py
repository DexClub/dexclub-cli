#!/usr/bin/env python3
from __future__ import annotations

from contextlib import contextmanager
from dataclasses import dataclass
from datetime import datetime, timezone
import hashlib
import json
import os
import shutil
import tempfile
import time
import uuid
import zipfile
from pathlib import Path

STORAGE_VERSION = "v1"
WORK_ROOT_PARTS = (".dexclub-cli",)
DEFAULT_CACHE_ROOT_PARTS = ("cache",)
INPUTS_CACHE_PARTS = (STORAGE_VERSION, "inputs")
EXPORT_AND_SCAN_CACHE_PARTS = (STORAGE_VERSION, "export-and-scan")
RUNS_ROOT_PARTS = ("runs", STORAGE_VERSION)
TMP_ROOT_PARTS = ("tmp",)


@dataclass(frozen=True, slots=True)
class CachedInputRef:
    input_kind: str
    cache_key: str
    cache_dir: Path
    original_path: Path
    cached_path: Path | None = None
    extracted_dex_dir: Path | None = None
    extracted_dex_paths: tuple[Path, ...] = ()


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def _resolve_configured_dir(env_name: str) -> Path | None:
    raw_value = os.environ.get(env_name)
    if raw_value is None:
        return None
    stripped = raw_value.strip()
    if not stripped:
        return None
    return Path(stripped).expanduser().resolve()


def workspace_root() -> Path:
    return Path.cwd().resolve()


def work_root() -> Path:
    configured = _resolve_configured_dir("DEXCLUB_ANALYST_WORK_ROOT")
    if configured is not None:
        return configured
    return workspace_root().joinpath(*WORK_ROOT_PARTS).resolve()


def cache_root() -> Path:
    configured = _resolve_configured_dir("DEXCLUB_ANALYST_CACHE_DIR")
    if configured is not None:
        return configured
    return work_root().joinpath(*DEFAULT_CACHE_ROOT_PARTS).resolve()


def runs_root() -> Path:
    return work_root().joinpath(*RUNS_ROOT_PARTS).resolve()


def inputs_cache_root() -> Path:
    return cache_root().joinpath(*INPUTS_CACHE_PARTS).resolve()


def export_and_scan_cache_root() -> Path:
    return cache_root().joinpath(*EXPORT_AND_SCAN_CACHE_PARTS).resolve()


def helper_tmp_root() -> Path:
    return work_root().joinpath(*TMP_ROOT_PARTS).resolve()


def allocate_helper_output_dir(prefix: str) -> Path:
    ensure_dir(helper_tmp_root())
    return Path(tempfile.mkdtemp(prefix=prefix, dir=str(helper_tmp_root()))).resolve()


def ensure_default_run_root(run_id: str) -> Path:
    return runs_root() / run_id


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as src:
        while True:
            chunk = src.read(1024 * 1024)
            if not chunk:
                break
            digest.update(chunk)
    return digest.hexdigest()


def ensure_dir(path: Path) -> Path:
    path.mkdir(parents=True, exist_ok=True)
    return path


def write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp_path = path.parent / f".{path.name}.tmp-{uuid.uuid4().hex[:8]}"
    try:
        tmp_path.write_text(content, encoding="utf-8")
        tmp_path.replace(path)
    finally:
        if tmp_path.exists():
            tmp_path.unlink()


def write_json(path: Path, payload: dict[str, object]) -> None:
    write_text(path, json.dumps(payload, ensure_ascii=False, indent=2) + "\n")


def lock_path(target: Path) -> Path:
    return target.parent / f".{target.name}.lock"


@contextmanager
def filesystem_lock(
    target: Path,
    *,
    timeout_seconds: float = 30.0,
    poll_interval_seconds: float = 0.1,
):
    lock_dir = lock_path(target)
    lock_dir.parent.mkdir(parents=True, exist_ok=True)
    deadline = time.monotonic() + timeout_seconds
    while True:
        try:
            lock_dir.mkdir(parents=False, exist_ok=False)
            write_json(
                lock_dir / "owner.json",
                {
                    "pid": os.getpid(),
                    "created_at": utc_now_iso(),
                    "target": str(target.resolve()),
                },
            )
            break
        except FileExistsError:
            if time.monotonic() >= deadline:
                raise TimeoutError(f"Timed out waiting for lock: {lock_dir}")
            time.sleep(poll_interval_seconds)
    try:
        yield
    finally:
        shutil.rmtree(lock_dir, ignore_errors=True)


def build_input_meta(*, input_kind: str, original_path: Path, sha256: str) -> dict[str, object]:
    stat = original_path.stat()
    return {
        "input_kind": input_kind,
        "original_path": str(original_path),
        "file_name": original_path.name,
        "size": stat.st_size,
        "mtime": stat.st_mtime,
        "sha256": sha256,
        "created_at": utc_now_iso(),
    }


def _move_tmp_dir(tmp_dir: Path, final_dir: Path) -> None:
    try:
        tmp_dir.replace(final_dir)
    except FileExistsError:
        shutil.rmtree(tmp_dir, ignore_errors=True)


def _sorted_apk_dex_entries(apk_path: Path) -> list[str]:
    entries: list[str] = []
    with zipfile.ZipFile(apk_path) as apk_zip:
        for info in apk_zip.infolist():
            if info.filename == "classes.dex" or (
                info.filename.startswith("classes")
                and info.filename.endswith(".dex")
            ):
                entries.append(info.filename)

    def sort_key(name: str) -> tuple[int, str]:
        if name == "classes.dex":
            return (1, name)
        suffix = name.removeprefix("classes").removesuffix(".dex")
        if suffix.isdigit():
            return (int(suffix), name)
        return (10**9, name)

    entries.sort(key=sort_key)
    return entries


def _is_valid_extracted_dex_dir(extracted_dir: Path, entry_names: list[str]) -> bool:
    return extracted_dir.is_dir() and all((extracted_dir / entry_name).is_file() for entry_name in entry_names)


def detect_cached_input(path: str | Path) -> CachedInputRef | None:
    path_obj = Path(path).expanduser().resolve()
    cache_root = inputs_cache_root()
    try:
        relative_parts = path_obj.relative_to(cache_root).parts
    except ValueError:
        return None

    if len(relative_parts) >= 4 and relative_parts[0] == "apk" and relative_parts[2] == "extracted-dex":
        cache_hash = relative_parts[1]
        cache_dir = cache_root / "apk" / cache_hash
        return CachedInputRef(
            input_kind="apk",
            cache_key=f"apk:{cache_hash}",
            cache_dir=cache_dir,
            original_path=path_obj,
            cached_path=path_obj,
            extracted_dex_dir=cache_dir / "extracted-dex",
            extracted_dex_paths=(path_obj,),
        )
    if len(relative_parts) == 3 and relative_parts[0] == "dex" and relative_parts[2] == "input.dex":
        cache_hash = relative_parts[1]
        cache_dir = cache_root / "dex" / cache_hash
        return CachedInputRef(
            input_kind="dex",
            cache_key=f"dex:{cache_hash}",
            cache_dir=cache_dir,
            original_path=path_obj,
            cached_path=path_obj,
        )
    return None


def ensure_dex_input_cache(path: str | Path) -> CachedInputRef:
    path_obj = Path(path).expanduser().resolve()
    cached = detect_cached_input(path_obj)
    if cached is not None:
        return cached

    digest = sha256_file(path_obj)
    cache_dir = inputs_cache_root() / "dex" / digest
    cached_path = cache_dir / "input.dex"
    meta_path = cache_dir / "input-meta.json"
    with filesystem_lock(cache_dir):
        if cached_path.is_file() and meta_path.is_file():
            return CachedInputRef(
                input_kind="dex",
                cache_key=f"dex:{digest}",
                cache_dir=cache_dir,
                original_path=path_obj,
                cached_path=cached_path,
            )

        ensure_dir(cache_dir.parent)
        tmp_dir = cache_dir.parent / f".{digest}.tmp-{uuid.uuid4().hex[:8]}"
        shutil.rmtree(tmp_dir, ignore_errors=True)
        tmp_dir.mkdir(parents=True, exist_ok=False)
        try:
            shutil.copy2(path_obj, tmp_dir / "input.dex")
            write_json(
                tmp_dir / "input-meta.json",
                build_input_meta(input_kind="dex", original_path=path_obj, sha256=digest),
            )
            _move_tmp_dir(tmp_dir, cache_dir)
        finally:
            shutil.rmtree(tmp_dir, ignore_errors=True)

    return CachedInputRef(
        input_kind="dex",
        cache_key=f"dex:{digest}",
        cache_dir=cache_dir,
        original_path=path_obj,
        cached_path=cached_path,
    )


def ensure_apk_input_cache(path: str | Path, *, ensure_extracted_dex: bool) -> CachedInputRef:
    path_obj = Path(path).expanduser().resolve()
    digest = sha256_file(path_obj)
    cache_dir = inputs_cache_root() / "apk" / digest
    meta_path = cache_dir / "input-meta.json"

    with filesystem_lock(cache_dir):
        ensure_dir(cache_dir)
        if not meta_path.is_file():
            write_json(
                meta_path,
                build_input_meta(input_kind="apk", original_path=path_obj, sha256=digest),
            )

        extracted_dir = cache_dir / "extracted-dex"
        extracted_paths: tuple[Path, ...] = ()
        if ensure_extracted_dex:
            entry_names = _sorted_apk_dex_entries(path_obj)
            if not _is_valid_extracted_dex_dir(extracted_dir, entry_names):
                if extracted_dir.exists():
                    shutil.rmtree(extracted_dir, ignore_errors=True)
                tmp_dir = cache_dir / f".extracted-dex.tmp-{uuid.uuid4().hex[:8]}"
                shutil.rmtree(tmp_dir, ignore_errors=True)
                tmp_dir.mkdir(parents=True, exist_ok=False)
                try:
                    with zipfile.ZipFile(path_obj) as apk_zip:
                        for entry_name in entry_names:
                            extracted_path = tmp_dir / entry_name
                            extracted_path.parent.mkdir(parents=True, exist_ok=True)
                            with apk_zip.open(entry_name) as src, extracted_path.open("wb") as dst:
                                dst.write(src.read())
                    _move_tmp_dir(tmp_dir, extracted_dir)
                finally:
                    shutil.rmtree(tmp_dir, ignore_errors=True)
            extracted_paths = tuple((extracted_dir / entry_name).resolve() for entry_name in entry_names)

    return CachedInputRef(
        input_kind="apk",
        cache_key=f"apk:{digest}",
        cache_dir=cache_dir,
        original_path=path_obj,
        extracted_dex_dir=extracted_dir if ensure_extracted_dex else None,
        extracted_dex_paths=extracted_paths,
    )

#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path


def dex_name_sort_key(name: str) -> tuple[int, str]:
    if name == "classes.dex":
        return (1, name)
    suffix = name.removeprefix("classes").removesuffix(".dex")
    if suffix.isdigit():
        return (int(suffix), name)
    return (10**9, name)


def sort_dex_names(names: list[str]) -> list[str]:
    return sorted(names, key=dex_name_sort_key)


def sort_dex_paths(paths: list[str]) -> list[str]:
    return sorted(
        paths,
        key=lambda path: (
            dex_name_sort_key(Path(path).name),
            str(Path(path)),
        ),
    )

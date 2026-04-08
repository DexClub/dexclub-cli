# Strings Workflow

Goal: find strings used by a target class or narrow methods by known string evidence.

## Current best path

1. If you already know the target string, start with `find-method` and `usingStrings`.
2. If you only know the class, narrow the class first with `find-class`.
3. If query results are still too broad, export the target class and inspect the exported code for string usage context.

## Important limit

The current released CLI can filter methods by string usage, but it does not provide a dedicated one-shot command that enumerates every string used by a class from an `apk` alone.

Treat full string extraction as a multi-step process:

- narrow the class or method
- query for strong string evidence when known
- export code when exhaustive inspection is needed

## Helper path

When you already have an exported Java or smali file, use:

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/scan_exported_code.py \
  --input /path/to/TargetClass.java \
  --mode strings
```

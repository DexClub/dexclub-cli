# Export Capability

Use export commands when you need class-level code artifacts for manual or scripted inspection.

## Available commands

- `export-dex`
- `export-smali`
- `export-java`

## Current limits

- Export commands currently support a single `dex` input.
- Export commands do not directly accept an `apk` input.
- If the user only has an `apk`, do not claim the export can be done in one step through the current released CLI.

## When to export

- analyze method logic after you have already narrowed the class
- inspect constants, field access, branch structure, and callees
- prepare downstream scanning outside the CLI when query filters are no longer enough

## Example

```bash
bash ./skills/dexclub-cli-launcher/launcher/scripts/run_latest_release.sh -- export-java \
  --input ./inputs/classes.dex \
  --class com.example.TargetClass \
  --output ./artifacts/TargetClass.java
```

## Stable analyst entry

For method-level logic analysis, prefer `analyze.py`:

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py run \
  --task-type summarize_method_logic \
  --input-json '{"input":["./inputs/classes.dex"],"method_anchor":{"class_name":"com.example.TargetClass","method_name":"targetMethod"}}'
```

If the input is an APK, `analyze.py` can insert the dex-resolution step before export.

## Internal helpers

If you already have a dex input and need direct export-plus-scan debugging, `export_and_scan.py` remains available as an internal helper:

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/export_and_scan.py \
  --input-dex ./inputs/classes.dex \
  --class com.example.TargetClass \
  --mode summary
```

Prefer `--language smali` when you need the most reliable export path or method-level bytecode details.

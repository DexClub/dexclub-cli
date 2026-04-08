# Method Logic Workflow

Goal: explain what a target method is doing.

## Current best path

1. Locate the target class and method with `find-class` or `find-method`.
2. Export the containing class with `export-smali` or `export-java`.
3. Summarize the method through:
   - branch structure
   - string and numeric constants
   - field reads and writes
   - direct callees
   - return value behavior

## Interpretation rule

Prefer `export-smali` when bytecode-level behavior matters.
Prefer `export-java` when a higher-level control-flow summary is enough and the decompiled output is readable.

Do not pretend the current released CLI has a dedicated `analyze-method` command. This workflow is intentionally multi-step.

## Helper path

For exported code:

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/scan_exported_code.py \
  --input /path/to/TargetClass.java \
  --method targetMethod \
  --mode summary
```

For direct dex-to-summary composition:

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/export_and_scan.py \
  --input-dex /path/to/classes.dex \
  --class com.example.TargetClass \
  --method targetMethod \
  --mode summary
```

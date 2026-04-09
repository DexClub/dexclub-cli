# Call Graph Workflow

Goal: move from one known method to its callers, callees, or nearby logic.

## Current best path

1. Identify the anchor method first.
2. Use `find-method` with matcher constraints around:
   - `invokeMethods` when you want candidate callers of a known target method
   - `callerMethods` when you want candidate callees constrained by known callers
3. Narrow by package, declaring class, strings, or numbers whenever possible.
4. Export the most relevant classes when matcher-only reasoning stops being reliable.

## Interpretation rule

This workflow is best for targeted traversal, not whole-program call graph dumping.

If the user asks for logic in "methods called by A" or "methods calling A", keep the scope narrow, search in layers, and export only after the candidate set becomes manageable.

## Stable analyst entry

To search for methods that call a known anchor:

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py run \
  --task-type trace_callers \
  --input-json '{"input":["./inputs/app.apk"],"method_anchor":{"class_name":"com.example.TargetClass","method_name":"targetMethod"}}'
```

To search for direct callees of a known anchor:

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py run \
  --task-type trace_callees \
  --input-json '{"input":["./inputs/app.apk"],"method_anchor":{"class_name":"com.example.EntryClass","method_name":"entryMethod"}}'
```

## Internal helper path

For low-level debugging of matcher composition, the helper script remains available:

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/run_find.py method \
  --input ./inputs/app.apk \
  --invoke-method com.example.TargetClass#targetMethod \
  --output-format json
```

To search for methods constrained by known callers:

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/run_find.py method \
  --input ./inputs/app.apk \
  --caller-method com.example.EntryClass#entryMethod \
  --output-format json
```

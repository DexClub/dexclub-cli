# Numbers Workflow

Goal: reason about numeric constants used by a target class or method.

## Current best path

1. If you already have candidate constants, use `find-method` with `usingNumbers`.
2. If you only know the class or method name, narrow the target first through `find-class` or `find-method`.
3. Export code when you need broader constant inspection that the current query layer cannot enumerate directly.

## Important limit

`usingNumbers` is a matcher input. It is not a built-in constant dump.

That means the current released CLI can answer:

- which methods use `1234`
- whether a narrowed method or class likely uses a known constant

It cannot directly answer, in one step:

- list every numeric constant used by the target class

For that task, the current workflow is:

- narrow the target
- export code
- inspect or scan the exported artifact outside the query layer

## Helper path

When you already have an exported Java or smali file, use:

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/scan_exported_code.py \
  --input ./artifacts/TargetClass.java \
  --mode numbers
```

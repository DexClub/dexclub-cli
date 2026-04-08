# Analyst Layer

The `analyst/` layer explains how to turn the released `dexclub-cli` into a practical reverse-analysis assistant.

It does not replace the launcher. Instead, it assumes the launcher can already execute the real CLI and focuses on orchestration:

- which CLI command to run first
- how to compose JSON matchers
- when to switch from search to export
- how to interpret current CLI limits

## Structure

- `references/`: generated matcher reference and schema
- `capabilities/`: current command-level guidance
- `workflows/`: problem-oriented multi-step analysis playbooks
- `scripts/`: maintenance or helper scripts owned by the analysis layer

## Current helper scripts

- `scripts/build_query.py`
  - generate common `find-class`, `find-method`, or `find-field` JSON without hand-writing nested matcher objects
- `scripts/run_find.py`
  - generate a common query JSON shape and immediately execute the matching `find-*` command through the launcher layer
- `scripts/scan_exported_code.py`
  - scan exported Java or smali for strings, numbers, calls, and field access
  - optionally narrow the scan to a single method
- `scripts/export_and_scan.py`
  - call the launcher layer to export a target class from a dex file
  - immediately run the exported-code scanner on the result
- `scripts/resolve_apk_dex.py`
  - extract APK dex entries and resolve which single dex contains a target class before export-based analysis
- `scripts/plan_schema.py`
  - define the version 1 task registry, planner constants, and shared schema defaults
- `scripts/planner.py`
  - normalize task inputs, run preflight checks, and emit structured execution plans
- `scripts/runner.py`
  - execute structured plans, capture helper subprocess output, and normalize final result payloads
- `scripts/analyze.py`
  - user-facing entry point for `plan` and `run` subcommands on top of the version 1 planner/runner
- `scripts/validate_v1_sample.sh`
  - run a repeatable sample validation pass against the current version 1 planner/runner contract
- `scripts/generate_query_reference.py`
  - maintenance helper for regenerating the matcher reference

## Current scope

Today the analyst layer is workflow-driven. It documents how to combine the released CLI commands that already exist:

- `inspect`
- `find-class`
- `find-method`
- `find-field`
- `export-dex`
- `export-smali`
- `export-java`

It does not yet provide a dedicated one-shot command for every higher-level question. When the released CLI lacks a direct primitive, the analyst layer must describe a multi-step approach instead of pretending the capability exists.

## Version 1 planner entrypoint

The version 1 planner layer keeps JSON as the primary stdout contract:

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py plan \
  --task-type search_methods_by_string \
  --input-json '{"input":["./inputs/app.apk"],"string":"needle"}'
```

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py run \
  --task-type summarize_method_logic \
  --input-json '{"input":["./inputs/app.apk"],"method_anchor":{"class_name":"com.example.Target","method_name":"login"}}'
```

Descriptor-aware anchors are now supported for direct relation tracing and smali summarize:

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py run \
  --task-type trace_callees \
  --input-json '{"input":["./inputs/app.apk"],"method_anchor":{"class_name":"com.example.Target","method_name":"login","descriptor":"(Ljava/lang/String;)V"}}'
```

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py run \
  --task-type summarize_method_logic \
  --input-json '{"input":["./inputs/classes.dex"],"method_anchor":{"class_name":"com.example.Target","method_name":"login","descriptor":"Lcom/example/Target;->login(Ljava/lang/String;)V"}}'
```

Current exact-anchor limits:

- `trace_callers` and `trace_callees` accept either a relaxed `ClassName#methodName` anchor or a descriptor-aware anchor.
- `summarize_method_logic` accepts a descriptor-aware anchor on both the `smali` and `java` export paths.
- Planner-side `descriptor-aware + language=java` hard rejection has been removed in the current repository state.
- The current published release `v0.0.1` now includes the `A-09` `export-java` fix.
- `scripts/validate_v1_sample.sh` now covers both direct `export_and_scan.py` Java exact summarize and APK-backed `analyze.py run` Java exact summarize against the published release path.
- `summarize_method_logic` on `smali` now also includes `structured_summary`, with basic blocks, call/constant clusters, and `focus_snippets`.
- Large `smali` summarize results now include `large_method_analysis`, which groups method-call, string, number, field, and branch hotspots without removing the exported raw code artifact.

Observed result excerpts:

- `references/analyze-v1-examples.md`

Repeatable sample validation against the published release:

```bash
bash ./skills/dexclub-cli-launcher/analyst/scripts/validate_v1_sample.sh ./inputs/sample.apk
```

Default local storage layout:

- `analyze.py run` writes per-run artifacts under `build/dexclub-cli/runs/v1/<run-id>/`
- APK / dex input caches live under `build/dexclub-cli/cache/v1/inputs/`
- APK-backed summarize resolves `classes*.dex` into the APK cache, then exports scanned code into the run-local `exports/` directory

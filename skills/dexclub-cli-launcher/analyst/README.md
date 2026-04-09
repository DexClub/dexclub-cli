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
- `scripts/`: the stable analyst entry point plus internal helpers and maintenance scripts

## Script roles

- Stable external analyst entry:
  - `scripts/analyze.py`
  - use `plan` and `run` as the supported analyst-facing interface on top of the version 1 planner/runner
- Internal planner/runtime helpers:
  - `scripts/run_find.py`
  - `scripts/resolve_apk_dex.py`
  - `scripts/export_and_scan.py`
  - `scripts/planner.py`
  - `scripts/runner.py`
  - `scripts/plan_schema.py`
  - `scripts/scan_exported_code.py`
  - these scripts are helper implementation details behind `analyze.py`, not a stable external contract
- Additional maintenance helpers:
  - `scripts/build_query.py`
  - `scripts/generate_query_reference.py`
  - `scripts/validate_v1_sample.sh`

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

## Stable analyst entry point

The version 1 planner layer keeps JSON as the primary stdout contract. Treat `scripts/analyze.py` as the stable analyst-facing entry point; the other analyst scripts are internal helpers that may change as the planner/runner evolves.

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

- Default work root: `<workspace>/.dexclub-cli/`
- `analyze.py run` writes per-run artifacts under `<workspace>/.dexclub-cli/runs/v1/<run-id>/` unless `--artifact-root` overrides it
- Run-level files in the selected run root:
  - `run-meta.json`
  - `final_result.json`
  - `run-summary.json`
- Per-step outputs are grouped under `steps/<step-id>/`
  - `step-result.json`
  - `artifacts/`
  - `raw.stdout.log`
  - `raw.stderr.log`
- Runs root also keeps `<workspace>/.dexclub-cli/runs/v1/latest.json`
- Runs root also keeps `<workspace>/.dexclub-cli/runs/v1/reusable-step-index-v1.json` for cross-run `resolve_apk_dex` / `export_and_scan` reuse
- APK / dex input caches live under `<workspace>/.dexclub-cli/cache/v1/inputs/`
- APK-backed class resolution also keeps a `class-dex-index-v1.json` cache under the APK input cache directory
- `export_and_scan.py` also keeps derived export/scan cache entries under `<workspace>/.dexclub-cli/cache/v1/export-and-scan/`
- The export/scan cache keys are normalized by dex content plus export/scope arguments, so APK-extracted dex and direct dex inputs can reuse the same cached export/analysis result
- When a run step is reused from a prior run, the new `step_results[]` item includes `reused_from` metadata and still materializes the current run's step artifacts
- Override the work root with `DEXCLUB_ANALYST_WORK_ROOT`
- Override the cache root with `DEXCLUB_ANALYST_CACHE_DIR`
- `export_and_scan.py` now creates its default direct-run output directory under the analyst work root instead of the anonymous system temp directory

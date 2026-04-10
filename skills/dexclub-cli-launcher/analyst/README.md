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

Explicit workspace dex-set inputs are also supported through the same stable `input` field:

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py run \
  --task-type search_methods_by_string \
  --input-json '{"input":{"dex_dir":"./artifacts/demo_dex"},"string":"login"}'
```

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py run \
  --task-type summarize_method_logic \
  --input-json '{"input":{"dex_list":["./artifacts/demo_dex/classes.dex","./artifacts/demo_dex/classes2.dex"]},"method_anchor":{"class_name":"com.example.Target","method_name":"login"}}'
```

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py cache inspect --format json
python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py cache prune --format json
python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py cache clear --scope inputs --scope export-and-scan --format json
python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py runs latest --format json
python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py runs inspect --run-id <run-id> --format json
python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py runs inspect --run-id <run-id> --include-final-result --format json
python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py runs list --limit 10 --format json
```

`cache inspect` now also includes a `latest_run` section derived from `runs/v1/latest.json` and `run-summary.json`.
`runs latest` and `runs inspect` expose the same persisted run projection directly from `latest.json`, `run-summary.json`, and `final_result.json`.
`runs inspect --include-final-result` additionally embeds the persisted `final_result.json` payload under `run.final_result`.
`runs list` returns recent `run-summary.json` items ordered by `updated_at` descending, with `is_latest` plus the same reuse/cache counters.
- When a key artifact maps to one concrete target class, `run-summary.json.key_artifacts` now keeps both the artifact `path` and the target `class_name` / `package_name`.
- High-frequency summaries such as `summarize_method_logic`, `trace_callers`, and `trace_callees` now prefer surfacing the target class FQCN in `summary.text`, not only a generic count sentence.
- `search_methods_by_string` / `search_methods_by_number` keep the generic summary when hits span multiple classes, but now surface the target FQCN in `summary.text` once the visible hit set collapses to one concrete class.

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

Workspace dex-set rules:

- `input.dex_dir` only scans the first directory level for `.dex` files.
- `input.dex_list` accepts a string array, resolves all paths, removes duplicates, and then applies the same stable dex ordering as `classes.dex`, `classes2.dex`, ...
- Query tasks and `summarize_method_logic` both report `input_source`, so downstream consumers can distinguish `apk_direct`, `apk_cached_extracted_dex`, and `workspace_dex_set`.

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
- Runs root also keeps `<workspace>/.dexclub-cli/runs/v1/reusable-step-index-v1.json` for cross-run `run_find` / `resolve_apk_dex` / `export_and_scan` reuse
- Cross-run step reuse is also isolated by the selected `release_tag`, so switching the cached dexclub-cli release does not reuse older step results
- APK / dex input caches live under `<workspace>/.dexclub-cli/cache/v1/inputs/`
- APK-backed class resolution also keeps a `class-dex-index-v1.json` cache under the APK input cache directory
- `export_and_scan.py` also keeps derived export/scan cache entries under `<workspace>/.dexclub-cli/cache/v1/export-and-scan/`
- The export/scan cache keys are normalized by dex content plus export/scope arguments, so APK-extracted dex and direct dex inputs can reuse the same cached export/analysis result
- When a run step is reused from a prior run, the new `step_results[]` item includes `reused_from` metadata and still materializes the current run's step artifacts
- `analyze.py run` now also exposes top-level `reused_step_count`, `reused_step_kinds`, and `cache_hit_count` aggregated from `step_results[]`
- `analyze.py run` now also exposes task-level reasoning buckets under `verifiedFacts`, `inferences`, `unknowns`, and `nextChecks`
- `run-summary.json` and `latest.json` now mirror the same reuse/cache counters for lightweight run inspection
- Invalid `reusable-step-index-v1.json` entries are pruned automatically before new runs continue
- `analyze.py cache clear` manages `inputs`, `export-and-scan`, `reusable-steps`, and `tmp`; when no `--scope` is provided it clears all of them
- Internal helper JSON mode is now strict: `run_find.py` re-emits one pure JSON document from the formal `output-file`, and the runner no longer guesses a JSON start offset from helper stdout
- Internal helper argument contracts are now CLI-aligned on `--input` and `--output-format`; legacy helper-only flags such as `--input-apk`, `--input-dex`, and `--format` remain accepted as compatibility aliases
- Direct helper failures now print `Cause:` plus `Recommended action:` so common misuse can be corrected without rereading the workflow docs
- Override the work root with `DEXCLUB_ANALYST_WORK_ROOT`
- Override the cache root with `DEXCLUB_ANALYST_CACHE_DIR`
- `export_and_scan.py` now creates its default direct-run output directory under the analyst work root instead of the anonymous system temp directory

Reuse/cache counter contract:

- `reused_step_count`: current run count of `step_results[]` items carrying `reused_from`
- `reused_step_kinds`: deduplicated step kinds for reused steps, kept in first-seen step order
- `cache_hit_count`: current run count of step results whose normalized `result.cache_hit` is `true`
- `run-summary.json` mirrors the same three fields from `final_result.json`
- `latest.json` mirrors the same three fields from the selected `run-summary.json`
- `cache inspect.latest_run` reads `latest.json` plus `run-summary.json`, then exposes `run_id`, `task_type`, `status`, `summary_path`, `summary_text`, and the same three counters

Task-level reasoning contract:

- `verifiedFacts`: statements directly supported by current run outputs or persisted planner/runtime state
- `inferences`: bounded interpretations derived from those verified facts
- `unknowns`: unresolved points that the current run still leaves open
- `nextChecks`: concrete follow-up checks or reruns suggested by the current status, recommendations, and limits
- When available, `verifiedFacts` and `inferences` attach compact evidence locators such as `source_path` and `line_numbers`

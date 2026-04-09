---
name: dexclub-cli-launcher
description: Prepare and run the cached dexclub-cli release artifact, then compose reverse-analysis workflows through the internal analyst layer.
---

# DexClub CLI Launcher

Use this skill as the single entry point for cached `dexclub-cli` execution and higher-level reverse-analysis workflows.

The implementation is layered internally:

- `launcher/`: release cache resolution, platform detection, and CLI execution
- `analyst/`: query references, capability notes, and workflow guidance for combining `inspect`, `find-*`, and `export-*`

## Internal entry points

- Runtime launcher:
  - Unix-like: `launcher/scripts/run_latest_release.sh`
  - Windows: `launcher/scripts/run_latest_release.bat`
- Analysis references:
  - Query reference: `analyst/references/query-json.md`
  - Query schema: `analyst/references/query-json.schema.json`
  - Capability docs: `analyst/capabilities/`
  - Workflow docs: `analyst/workflows/`
- Stable analyst entry point:
  - `analyst/scripts/analyze.py`
- Internal analyst helpers:
  - `analyst/scripts/run_find.py`
  - `analyst/scripts/resolve_apk_dex.py`
  - `analyst/scripts/export_and_scan.py`

## Recommended operating model

1. Use the launcher layer first.
   - Resolve the cache root:
     - `bash ./skills/dexclub-cli-launcher/launcher/scripts/run_latest_release.sh --print-cache-path`
     - `.\skills\dexclub-cli-launcher\launcher\scripts\run_latest_release.bat --print-cache-path`
   - Resolve the normalized platform id:
     - `bash ./skills/dexclub-cli-launcher/launcher/scripts/run_latest_release.sh --print-platform`
     - `.\skills\dexclub-cli-launcher\launcher\scripts\run_latest_release.bat --print-platform`
2. Reuse local cache by default.
   - Only touch GitHub Release when:
     - no compatible local cache exists yet
     - the user explicitly asks to refresh or update the cache
3. Use the analyst layer to decide how to compose the real CLI.
   - Query composition and matcher fields: `analyst/references/query-json.md`
   - Current query behavior: `analyst/capabilities/query.md`
   - Current export behavior: `analyst/capabilities/export.md`
   - Strings workflow: `analyst/workflows/strings.md`
   - Numbers workflow: `analyst/workflows/numbers.md`
   - Method logic workflow: `analyst/workflows/method-logic.md`
   - Call graph workflow: `analyst/workflows/callgraph.md`
4. Treat `analyst/scripts/analyze.py` as the stable analyst-facing script entry point.
   - Use `plan` when you only need the structured plan.
   - Use `run` when you want the planner/runner to execute the workflow and emit the final JSON result.
   - Do not treat `run_find.py`, `resolve_apk_dex.py`, or `export_and_scan.py` as stable external interfaces; they are internal helpers behind `analyze.py`.

## Launcher rules

- Do not refresh from GitHub unless the user explicitly asks, or no compatible local cache exists.
- If remote access has already been disabled after repeated failures, only reset it when the user explicitly asks to retry:
  - `bash ./skills/dexclub-cli-launcher/launcher/scripts/run_latest_release.sh --reset-remote-failures --update-cache --prepare-only`
  - `.\skills\dexclub-cli-launcher\launcher\scripts\run_latest_release.bat --reset-remote-failures --update-cache --prepare-only`
- If you only need to prepare or inspect the cached artifact, use:
  - `bash ./skills/dexclub-cli-launcher/launcher/scripts/run_latest_release.sh --prepare-only`
  - `bash ./skills/dexclub-cli-launcher/launcher/scripts/run_latest_release.sh --print-launcher`
  - `bash ./skills/dexclub-cli-launcher/launcher/scripts/run_latest_release.sh --print-latest-tag`
  - `.\skills\dexclub-cli-launcher\launcher\scripts\run_latest_release.bat --prepare-only`
  - `.\skills\dexclub-cli-launcher\launcher\scripts\run_latest_release.bat --print-launcher`
  - `.\skills\dexclub-cli-launcher\launcher\scripts\run_latest_release.bat --print-latest-tag`

## CLI execution

Use the release launcher directly when you need the real `dexclub-cli` behavior. Do not invent wrapper commands that are not part of the released CLI.

Examples:

```bash
bash ./skills/dexclub-cli-launcher/launcher/scripts/run_latest_release.sh -- --help
```

```bash
bash ./skills/dexclub-cli-launcher/launcher/scripts/run_latest_release.sh -- inspect --input ./inputs/app.apk
```

```bash
bash ./skills/dexclub-cli-launcher/launcher/scripts/run_latest_release.sh -- find-method \
  --input ./inputs/classes.dex \
  --query-json '{"matcher":{"usingStrings":[{"value":"needle","matchType":"Contains","ignoreCase":true}]}}'
```

```bash
bash ./skills/dexclub-cli-launcher/launcher/scripts/run_latest_release.sh -- export-smali \
  --input ./inputs/classes.dex \
  --class com.example.TargetActivity \
  --output ./artifacts/TargetActivity.smali
```

## Analyst guidance

Stable entry examples:

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

- Prefer `Contains` with `ignoreCase: true` for `usingStrings` unless exact matching is explicitly required.
- For class, method, and field identifiers, `Equals` is usually the better default.
- When the user asks which methods call a known target method, prefer `invokeMethods` on candidate callers.
- When the user asks to analyze method logic, export the target class first, then summarize branches, constants, field access, and callees.
- The current released CLI does not provide a direct one-shot command for entry activity resolution, Android resource enumeration, or full automatic constant extraction. Treat these as multi-step workflows.

## Behavior notes

- The launcher scripts never depend on GitHub Actions artifacts.
- Latest release discovery follows the GitHub release webpage redirect and does not depend on the GitHub API.
- `--print-latest-tag` prints the currently selected local cached tag and does not trigger a remote check.
- Remote failure state is isolated by repository and platform.
- The scripts normalize the current machine to `linux|macos|windows` and `x64|arm64`, then look for `dexclub-cli-<os>-<arch>.zip`.
- If the latest release does not publish a matching asset for the current platform, the launcher exits non-zero after printing the unsupported-platform message. Preserve that message exactly.
- The default analyst work root is `<workspace>/.dexclub-cli/`.
  - `analyze.py run` writes per-run artifacts under `<workspace>/.dexclub-cli/runs/v1/<run-id>/` unless `--artifact-root` overrides it.
  - Input caches live under `<workspace>/.dexclub-cli/cache/v1/inputs/`.
  - `export_and_scan.py` keeps derived export/scan caches under `<workspace>/.dexclub-cli/cache/v1/export-and-scan/`.
  - `analyze.py run` also keeps `<workspace>/.dexclub-cli/runs/v1/reusable-step-index-v1.json` to reuse prior `run_find` / `resolve_apk_dex` / `export_and_scan` steps across runs.
  - The run root now contains `run-meta.json`, `final_result.json`, `run-summary.json`, and per-step data under `steps/<step-id>/`.
  - Override the work root with `DEXCLUB_ANALYST_WORK_ROOT` when the current workspace should not own `.dexclub-cli/`.
  - Override the cache root with `DEXCLUB_ANALYST_CACHE_DIR` when input caches should live outside the default work root.
  - Direct helper runs such as `export_and_scan.py` now allocate their default output directory under the analyst work root instead of the anonymous system temp directory.

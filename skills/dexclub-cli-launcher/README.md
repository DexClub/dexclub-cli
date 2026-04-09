# dexclub-cli-launcher

This repository keeps the skill implementation in:

- `SKILL.md`
- `agents/openai.yaml`
- `launcher/README.md`
- `launcher/scripts/run_latest_release.sh`
- `launcher/scripts/run_latest_release.bat`
- `launcher/scripts/run_latest_release.ps1`
- `analyst/README.md`
- `analyst/references/query-json.md`
- `analyst/references/query-json.schema.json`
- `analyst/capabilities/`
- `analyst/workflows/`
- `analyst/scripts/generate_query_reference.py`

## Purpose

This skill remains a single externally delivered skill, but its internal implementation is now split into two layers:

- `launcher/`: release preparation, cache reuse, and real CLI execution
- `analyst/`: references, capability notes, and workflow guidance for multi-step reverse analysis

The runtime path only depends on the pre-generated files under `analyst/references/`. The generator script is a maintenance helper for refreshing those files when the query model changes.

It provides:

- automatic OS and architecture detection
- GitHub Release based download and cache refresh
- fixed local cache reuse
- extracted launcher discovery and execution
- direct `dexclub-cli` invocation through the cached release launcher
- string/class/method/field search support through the original CLI subcommands
- an explicit unsupported-platform failure message

## Maintenance Flow

The repository copy under `skills/dexclub-cli-launcher/` is the source of truth for this skill.

When the skill needs updates:

- modify the repository copy first
- finish the related code, docs, and minimal validation in the repository
- only then sync the completed result into the installed skill copy under `$CODEX_HOME/skills/dexclub-cli-launcher`

Do not edit the installed skill copy directly as the primary change path. Otherwise the repository version and the installed version drift immediately, and users who fetch the repository will still get stale skill files.

## Architecture Roadmap

This skill is expected to keep growing into a higher-level reverse-analysis assistant over time.

The planned evolution is incremental:

- Current stage:
  - keep a single externally delivered skill
  - keep the launcher and analyst concerns separated internally
  - use the analyst layer to document practical workflows around the current released CLI
- Mid stage:
  - grow helper scripts under `analyst/scripts/`
  - add more workflow-specific guidance without changing the one-entry-point model
- Later stage:
  - if analysis complexity grows, further separate `extractors/`, `analyzers/`, and orchestration helpers inside the analysis layer
  - only introduce a more structured interface such as MCP if the script-based workflow becomes a real maintenance bottleneck

Design principles for future changes:

- expose one user-facing skill, even if the internal implementation is layered
- keep launcher concerns separate from reverse-analysis workflow concerns
- prefer incremental restructuring over speculative architecture
- treat the current structure as a stepping stone, not a permanent ceiling

## Cache behavior

- The scripts reuse the selected local cache by default.
- The scripts do not check GitHub on every run when a compatible cache already exists.
- The scripts check the latest release only when:
  - no compatible local cache exists yet
  - the caller explicitly asks for `--update-cache`
- Latest release discovery follows the GitHub release webpage redirect and does not depend on the GitHub API.
- `--print-latest-tag` prints the currently selected local cached tag and does not trigger a remote check.
- If remote access fails during an initial fetch or an explicit update, the scripts warn at most twice.
- After two failed remote attempts, further remote checks are disabled until `--reset-remote-failures` is used.
- Remote failure state is isolated by repository and platform.

## Common commands

Unix-like:

```bash
bash ./skills/dexclub-cli-launcher/launcher/scripts/run_latest_release.sh --prepare-only
bash ./skills/dexclub-cli-launcher/launcher/scripts/run_latest_release.sh --update-cache --prepare-only
bash ./skills/dexclub-cli-launcher/launcher/scripts/run_latest_release.sh --reset-remote-failures --update-cache --prepare-only
bash ./skills/dexclub-cli-launcher/launcher/scripts/run_latest_release.sh -- --help
```

Windows:

```bat
.\skills\dexclub-cli-launcher\launcher\scripts\run_latest_release.bat --prepare-only
.\skills\dexclub-cli-launcher\launcher\scripts\run_latest_release.bat --update-cache --prepare-only
.\skills\dexclub-cli-launcher\launcher\scripts\run_latest_release.bat --reset-remote-failures --update-cache --prepare-only
.\skills\dexclub-cli-launcher\launcher\scripts\run_latest_release.bat -- --help
```

## Search commands

Use the original CLI command line from the project README and run it through the cached release launcher.

Unix-like:

```bash
bash ./skills/dexclub-cli-launcher/launcher/scripts/run_latest_release.sh -- find-class --input ./inputs/classes.dex --query-json '{"matcher":{"className":{"value":"SampleSearchTarget","matchType":"Contains","ignoreCase":true}}}' --output-format json --limit 20
bash ./skills/dexclub-cli-launcher/launcher/scripts/run_latest_release.sh -- find-method --input ./inputs/classes.dex --query-json '{"matcher":{"usingStrings":[{"value":"dexclub-needle-string","matchType":"Contains","ignoreCase":true}]}}'
bash ./skills/dexclub-cli-launcher/launcher/scripts/run_latest_release.sh -- find-class --input ./inputs/classes.dex --query-file ./queries/find-class.json --output-format json
bash ./skills/dexclub-cli-launcher/launcher/scripts/run_latest_release.sh -- find-method --input ./inputs/classes.dex --query-json '{"matcher":{"name":{"value":"exposeNeedle","matchType":"Equals"}}}'
bash ./skills/dexclub-cli-launcher/launcher/scripts/run_latest_release.sh -- find-field --input ./inputs/classes.dex --query-file ./queries/find-field.json --output-format json
```

Windows:

```bat
.\skills\dexclub-cli-launcher\launcher\scripts\run_latest_release.bat -- find-class --input .\inputs\classes.dex --query-json "{\"matcher\":{\"className\":{\"value\":\"SampleSearchTarget\",\"matchType\":\"Contains\",\"ignoreCase\":true}}}" --output-format json --limit 20
.\skills\dexclub-cli-launcher\launcher\scripts\run_latest_release.bat -- find-method --input .\inputs\classes.dex --query-json "{\"matcher\":{\"usingStrings\":[{\"value\":\"dexclub-needle-string\",\"matchType\":\"Contains\",\"ignoreCase\":true}]}}"
```

## Notes

- The scripts only use GitHub Release assets.
- They never depend on GitHub Actions artifacts.
- Use `--print-cache-path` when you need the current cache location.
- For string queries, prefer `Contains` with `ignoreCase: true` unless exact matching is explicitly required.
- `analyst/references/query-json.md` and `analyst/references/query-json.schema.json` are generated from the current query model by `analyst/scripts/generate_query_reference.py`.
- To regenerate them outside this repository layout, set `DEXCLUB_CLI_QUERY_SOURCE_DIR` to a dexkit source directory containing `query/` and `result/`, or set `DEXCLUB_CLI_REPO_ROOT` to a repository root that contains `dexkit/src/commonMain/kotlin/io/github/dexclub/dexkit/`.

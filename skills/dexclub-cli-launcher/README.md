# dexclub-cli-launcher

This repository keeps the skill implementation in:

- `SKILL.md`
- `agents/openai.yaml`
- `references/query-json.md`
- `references/query-json.schema.json`
- `scripts/run_latest_release.sh`
- `scripts/run_latest_release.bat`
- `scripts/run_latest_release.ps1`
- `scripts/generate_query_reference.py`

## Purpose

This skill prepares and runs a cached `dexclub-cli` release build for the current platform.

The runtime path only depends on the pre-generated files under `references/`. The generator script is a maintenance helper for refreshing those files when the query model changes.

It provides:

- automatic OS and architecture detection
- GitHub Release based download and cache refresh
- fixed local cache reuse
- extracted launcher discovery and execution
- direct `dexclub-cli` invocation through the cached release launcher
- string/class/method/field search support through the original CLI subcommands
- an explicit unsupported-platform failure message

## Cache behavior

- The scripts reuse the selected local cache by default.
- The scripts do not check GitHub on every run when a compatible cache already exists.
- The scripts check the latest release only when:
  - no compatible local cache exists yet
  - the caller explicitly asks for `--update-cache`
- Latest release discovery uses the GitHub latest-release API only.
- `--print-latest-tag` prints the currently selected local cached tag and does not trigger a remote check.
- If remote access fails during an initial fetch or an explicit update, the scripts warn at most twice.
- After two failed remote attempts, further remote checks are disabled until `--reset-remote-failures` is used.
- Remote failure state is isolated by repository and platform.

## Common commands

Unix-like:

```bash
bash ./skills/dexclub-cli-launcher/scripts/run_latest_release.sh --prepare-only
bash ./skills/dexclub-cli-launcher/scripts/run_latest_release.sh --update-cache --prepare-only
bash ./skills/dexclub-cli-launcher/scripts/run_latest_release.sh --reset-remote-failures --update-cache --prepare-only
bash ./skills/dexclub-cli-launcher/scripts/run_latest_release.sh -- --help
```

Windows:

```bat
.\skills\dexclub-cli-launcher\scripts\run_latest_release.bat --prepare-only
.\skills\dexclub-cli-launcher\scripts\run_latest_release.bat --update-cache --prepare-only
.\skills\dexclub-cli-launcher\scripts\run_latest_release.bat --reset-remote-failures --update-cache --prepare-only
.\skills\dexclub-cli-launcher\scripts\run_latest_release.bat -- --help
```

## Search commands

Use the original CLI command line from the project README and run it through the cached release launcher.

Unix-like:

```bash
bash ./skills/dexclub-cli-launcher/scripts/run_latest_release.sh -- find-class --input /path/to/classes.dex --query-json '{"matcher":{"className":{"value":"SampleSearchTarget","matchType":"Contains","ignoreCase":true}}}' --output-format json --limit 20
bash ./skills/dexclub-cli-launcher/scripts/run_latest_release.sh -- find-method --input /path/to/classes.dex --query-json '{"matcher":{"usingStrings":[{"value":"dexclub-needle-string","matchType":"Contains","ignoreCase":true}]}}'
bash ./skills/dexclub-cli-launcher/scripts/run_latest_release.sh -- find-class --input /path/to/classes.dex --query-file /path/to/find-class.json --output-format json
bash ./skills/dexclub-cli-launcher/scripts/run_latest_release.sh -- find-method --input /path/to/classes.dex --query-json '{"matcher":{"name":{"value":"exposeNeedle","matchType":"Equals"}}}'
bash ./skills/dexclub-cli-launcher/scripts/run_latest_release.sh -- find-field --input /path/to/classes.dex --query-file /path/to/find-field.json --output-format json
```

Windows:

```bat
.\skills\dexclub-cli-launcher\scripts\run_latest_release.bat -- find-class --input C:\path\to\classes.dex --query-json "{\"matcher\":{\"className\":{\"value\":\"SampleSearchTarget\",\"matchType\":\"Contains\",\"ignoreCase\":true}}}" --output-format json --limit 20
.\skills\dexclub-cli-launcher\scripts\run_latest_release.bat -- find-method --input C:\path\to\classes.dex --query-json "{\"matcher\":{\"usingStrings\":[{\"value\":\"dexclub-needle-string\",\"matchType\":\"Contains\",\"ignoreCase\":true}]}}"
```

## Notes

- The scripts only use GitHub Release assets.
- They never depend on GitHub Actions artifacts.
- Use `--print-cache-path` when you need the current cache location.
- For string queries, prefer `Contains` with `ignoreCase: true` unless exact matching is explicitly required.
- `references/query-json.md` and `references/query-json.schema.json` are generated from the current query model by `scripts/generate_query_reference.py`.
- To regenerate them outside this repository layout, set `DEXCLUB_CLI_QUERY_SOURCE_DIR` to a dexkit source directory containing `query/` and `result/`, or set `DEXCLUB_CLI_REPO_ROOT` to a repository root that contains `dexkit/src/commonMain/kotlin/io/github/dexclub/dexkit/`.

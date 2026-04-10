# Launcher Layer

The `launcher/` layer is responsible for one thing only: prepare a usable cached `dexclub-cli` release artifact for the current machine, then execute it.

## Responsibilities

- normalize OS and architecture
- locate or refresh the local release cache
- guard remote retries and failure backoff
- resolve the extracted launcher path
- execute the released CLI without adding another wrapper contract

## Entry points

- Unix-like: `scripts/run_latest_release.sh`
- Windows: `scripts/run_latest_release.bat`
- PowerShell helper: `scripts/run_latest_release.ps1`

## Common commands

```bash
bash ./skills/dexclub-cli-launcher/launcher/scripts/run_latest_release.sh --print-cache-path
bash ./skills/dexclub-cli-launcher/launcher/scripts/run_latest_release.sh --print-platform
bash ./skills/dexclub-cli-launcher/launcher/scripts/run_latest_release.sh --prepare-only
bash ./skills/dexclub-cli-launcher/launcher/scripts/run_latest_release.sh --print-launcher
bash ./skills/dexclub-cli-launcher/launcher/scripts/run_latest_release.sh -- --help
```

If this launcher is accidentally executed with `python3`, it now exits with a direct usage hint instead of surfacing a raw Python `SyntaxError`.

## Operational rules

- Reuse local cache by default.
- Only refresh from GitHub when explicitly requested or when no compatible cache exists.
- Do not fold reverse-analysis logic into the launcher scripts.
- Keep the launcher layer stable even if the analyst layer grows.

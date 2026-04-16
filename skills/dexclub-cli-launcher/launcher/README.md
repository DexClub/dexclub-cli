# Launcher Layer

The `launcher/` layer is responsible for one thing only: prepare a usable cached `dexclub-cli` release artifact for the current machine, then execute it.

## Responsibilities

- normalize OS and architecture
- classify ARM64 Unix runtime ABI before selecting an artifact
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
bash ./skills/dexclub-cli-launcher/launcher/scripts/run_latest_release.sh --print-runtime-class
bash ./skills/dexclub-cli-launcher/launcher/scripts/run_latest_release.sh --prepare-only
bash ./skills/dexclub-cli-launcher/launcher/scripts/run_latest_release.sh --print-launcher
bash ./skills/dexclub-cli-launcher/launcher/scripts/run_latest_release.sh -- --help
```

If this launcher is accidentally executed with `python3`, it now exits with a direct usage hint instead of surfacing a raw Python `SyntaxError`.

## Operational rules

- Reuse local cache by default.
- Only refresh from GitHub when explicitly requested or when no compatible cache exists.
- On ARM64 Unix, choose the artifact by runtime ABI instead of shell origin.
  - `glibc + arm64 -> linux-arm64`
  - `bionic + arm64 -> android-arm64`
  - `musl` / `unknown` -> stop without fallback
- `--print-platform` returns the final artifact class, and `--print-runtime-class` exposes the ABI classification used for that choice.
- `android-arm64` is the Termux / Android `bionic` JVM CLI artifact, not an Android app.
- Do not fold reverse-analysis logic into the launcher scripts.
- Keep the launcher layer stable even if the analyst layer grows.

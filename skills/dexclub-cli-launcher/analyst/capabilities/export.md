# Export Capability

Use export commands when you need class-level code artifacts for manual or scripted inspection.

## Available commands

- `export-dex`
- `export-smali`
- `export-java`

## Current limits

- Export commands currently support a single `dex` input.
- Export commands do not directly accept an `apk` input.
- If the user only has an `apk`, do not claim the export can be done in one step through the current released CLI.

## When to export

- analyze method logic after you have already narrowed the class
- inspect constants, field access, branch structure, and callees
- prepare downstream scanning outside the CLI when query filters are no longer enough

## Example

```bash
bash ./skills/dexclub-cli-launcher/launcher/scripts/run_latest_release.sh -- export-java \
  --input /path/to/classes.dex \
  --class com.example.TargetClass \
  --output /tmp/TargetClass.java
```

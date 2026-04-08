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

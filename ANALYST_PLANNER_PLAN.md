# Analyst Planner Plan

## Status

This document defines the version 1 planning and execution model for the `skills/dexclub-cli-launcher` analyst layer.

It is intentionally implementation-oriented, but it is not code. The goal is to stabilize the design before adding `plan_schema.py`, `planner.py`, `runner.py`, and `analyze.py` on top of the current launcher and analyst helper scripts.

## Scope Boundary

This plan applies only to the Python-based analyst layer under `skills/dexclub-cli-launcher/`.

It does not redefine the Kotlin module boundaries of the main repository:

- `cli/`
  - remains the user-facing command-line entry point for the main project
- `core/`
  - remains the stable business capability layer
- `dexkit/`
  - remains the KMP DexKit wrapper layer

Version 1 planner and runner work above the released `dexclub-cli` behavior exposed through the launcher skill. They must not introduce new assumptions about `cli / core / dexkit` internals as part of this analyst-layer design.

## Foundation

The repository already has three important layers in place:

1. `launcher/`
   - prepares and runs the cached `dexclub-cli` release
2. analyst low-level helpers
   - query composition and execution
   - export and scan
   - exported-code analysis
3. analyst documentation
   - capability notes
   - workflow notes

The next step is not to replace these layers. It is to add a constrained planning and execution layer above them.

## Goal

Introduce a structured planner and runner that can:

- classify a reverse-analysis request into a small set of supported task types
- build a structured execution plan
- execute the plan through existing helper scripts and launcher-backed CLI calls
- produce structured output plus an optional human-readable summary

## Non-Goals

Version 1 will not:

- solve arbitrary natural-language reverse-engineering tasks
- perform unbounded multi-hop analysis
- auto-resolve Android entry components
- auto-analyze Android resources
- infer reflection-heavy behavior automatically
- introduce MCP just for the sake of structure

## Guiding Principles

- Keep one user-facing skill.
- Keep launcher concerns separate from analyst concerns.
- Treat planner output as structured data, not free-form reasoning.
- Keep the first task set intentionally small and stable.
- Prefer explicit inputs over broad guessing.
- Allow partial support and explicit refusal instead of pretending a task is fully automated.

## Current Constraints

Version 1 planning must stay aligned with the helper scripts that already exist under `analyst/scripts/`.

Current helper constraints:

- `run_find.py`
  - executes one `find-*` command through the launcher
  - supports direct query-based search workflows
  - currently accepts APK or dex inputs and can pass repeated `--input`
  - currently behaves as an execution helper, not a normalized result contract
- `export_and_scan.py`
  - exports exactly one class from exactly one dex input
  - cannot operate on APK input directly
  - scopes method analysis by method name only
  - does not resolve overload ambiguity by descriptor
  - currently emits helper-shaped payload keys such as `exportPath`
- `code_analysis.py` and `scan_exported_code.py`
  - analyze exported Java or smali text
  - provide direct body-level signals such as strings, numbers, field access, and direct calls
  - do not provide a complete semantic call graph

The planner must not describe capabilities that these helpers cannot yet support without additional implementation work.

Current helper summary:

- `run_find.py`
  - input shape: CLI-style flags
  - output shape: raw `dexclub-cli` stdout
  - hard limits: no total-hit count, no planner-owned normalization, no overload disambiguation
- `export_and_scan.py`
  - input shape: one dex path plus one class anchor and optional method name
  - output shape: helper-level JSON or text summary
  - hard limits: no APK input, no descriptor-enforced method scoping, one exported class per run
- `scan_exported_code.py`
  - input shape: one exported Java or smali file
  - output shape: code-analysis summary slices
  - hard limits: text-level scanning only, not semantic program analysis

## Open Assumptions

The following items should be treated as explicit implementation assumptions until they are verified against real released CLI output:

- the exact JSON field names returned by `find-method --output-format json`
- whether direct search results reliably include method descriptors for all relevant hit types
- whether direct search results reliably include source dex identity when multiple dex inputs are used
- whether relation-style hits returned through `invokeMethods` and `callerMethods` expose enough data for precise normalization without extra lookup

Version 1 implementation should verify these assumptions early and update this plan or the runner normalization rules before claiming a stable public contract.

## Layering

The skill should keep the following layered structure:

1. `launcher/`
   - release preparation
   - cache reuse
   - real CLI execution
2. analyst low-level execution primitives
   - `build_query.py`
   - `run_find.py`
   - `export_and_scan.py`
   - `scan_exported_code.py`
   - `code_analysis.py`
3. new analyst planning layer
   - `plan_schema.py`
   - `planner.py`
   - `runner.py`
   - `analyze.py`

## Version 1 Scope

### Supported task types

Version 1 supports exactly these task types:

- `search_methods_by_string`
- `search_methods_by_number`
- `summarize_method_logic`
- `trace_callers`
- `trace_callees`

No additional task types should be added before the first version is stable.

### Task registry

The implementation should keep one task registry as the source of truth for planner behavior.

Each task definition should declare:

- required arguments
- optional arguments
- accepted input kinds
- default planning strategy
- result shape
- current limits
- `max_direct_hits`

Version 1 should keep `max_direct_hits` explicit in the task registry instead of inferring it implicitly at runtime.

Recommended default:

- direct-search tasks default to `max_direct_hits = 20`
- runner-side threshold probing uses `internal_limit = max_direct_hits + 1` unless a task definition overrides it

### Threshold rule

If a task produces more than `max_direct_hits` results, the planner or runner must not auto-expand the search. It should return a structured narrowed-search recommendation instead.

Current CLI alignment:

- the current CLI applies `--limit` before emitting JSON or text output
- runner-side threshold detection therefore must not rely on a helper-reported total count that does not exist yet
- direct-search tasks should distinguish user-facing limit from internal execution limit
- for version 1 direct-search steps, the runner should request at least `max_direct_hits + 1` items from the underlying `run_find` execution path so threshold overflow can be detected without pretending the total count is known
- if threshold overflow is detected, the runner should return a narrowed-search recommendation instead of silently truncating and presenting the result as complete

Threshold-overflow result rule:

- threshold overflow is not an `execution_error`
- version 1 may return a truncated direct-hit payload together with a recommendation, but it must mark that payload as truncated explicitly
- when `truncated = true` because of threshold overflow, the exact total count may remain unknown in version 1
- in that case, `count` should reflect the number of returned items in the normalized payload, not a guessed global total
- if a task still returns a non-empty truncated preview, the final run `status` may remain `ok`
- if a task chooses not to return preview items after overflow and only emits narrowing guidance, the final run `status` should be `empty` rather than a fabricated success payload

External `limit` rule:

- external task input `limit` is a presentation constraint on returned items, not the sole execution constraint used for threshold detection
- for direct-search tasks, the runner may execute with an internal limit larger than the external `limit` so it can detect threshold overflow reliably
- if both are present, the final normalized payload should respect the external `limit`, while overflow detection may still rely on the larger internal execution window
- if results are truncated only because of an external `limit`, the payload should still mark `truncated = true`
- truncation caused only by an external `limit` does not require a narrowed-search recommendation by itself

## Inputs

### Shared input rules

- Every task must declare its accepted input types.
- External task field name remains `input` across version 1 tasks.
- External `input` may be provided as either:
  - one path string
  - an array of path strings
- The planner must normalize either form into the shared internal `paths` list.
- File paths must be validated before planning proceeds.
- The planner must not silently convert unsupported input types.
- The planner and runner must normalize external inputs into one shared internal input model.

### Normalized input model

After preflight, inputs should be normalized into one internal shape.

Required fields:

- `primary_kind`
- `paths`
- `path_count`

Allowed `primary_kind` values:

- `apk`
- `dex`
- `dex_set`
- `exported_code`

Normalization rules:

- single APK input -> `primary_kind = "apk"`
- single dex input -> `primary_kind = "dex"`
- multiple dex inputs -> `primary_kind = "dex_set"`
- exported Java or smali input -> `primary_kind = "exported_code"`
- mixed APK and dex inputs are invalid in version 1
- export-based tasks must require `primary_kind = "dex"`

Example shape:

```json
{
  "primary_kind": "dex",
  "paths": [
    "/path/to/classes.dex"
  ],
  "path_count": 1
}
```

### Task and input matrix

Version 1 accepted input kinds:

- `search_methods_by_string`
  - `apk`
  - `dex`
  - `dex_set`
- `search_methods_by_number`
  - `apk`
  - `dex`
  - `dex_set`
- `summarize_method_logic`
  - `dex`
  - exactly one dex path is required
- `trace_callers`
  - `apk`
  - `dex`
  - `dex_set`
- `trace_callees`
  - `apk`
  - `dex`
  - `dex_set`

The planner must reject any task and input-kind combination outside this matrix in version 1.

Version 1 treatment of `exported_code`:

- `exported_code` may remain in the normalized model for future expansion
- no version 1 task accepts `exported_code` as an external input kind
- if a version 1 task receives `exported_code`, the planner must return `unsupported`

### Task-specific external inputs

#### `search_methods_by_string`

Required:

- `input`
- `string`

Optional:

- `declared_class`
- `search_package`
- `limit`

#### `search_methods_by_number`

Required:

- `input`
- `number`

Optional:

- `declared_class`
- `search_package`
- `limit`

#### `summarize_method_logic`

Required:

- `input`
- `method_anchor`

Optional:

- `language`
- `mode`

#### `trace_callers`

Required:

- `input`
- `method_anchor`

Optional:

- `search_package`
- `limit`

#### `trace_callees`

Required:

- `input`
- `method_anchor`

Optional:

- `search_package`
- `limit`

## Method Anchor Model

Method-level tasks must not rely on `class_name + method_name` alone. That is not stable enough when overloads exist.

Required fields:

- `class_name`
- `method_name`

Optional fields:

- `descriptor`
- `params`
- `return_type`

Version 1 rule:

- the relaxed `class_name + method_name` form may be accepted when the user does not know the descriptor
- the relaxed form must be marked as potentially ambiguous
- the planner must record that ambiguity in `limits`
- if multiple overloads are found and the task requires a precise target, the runner must return `ambiguous` instead of guessing
- version 1 does not perform hidden overload resolution to upgrade a relaxed anchor into a precise anchor

Current helper alignment:

- the normalized planner model may carry `descriptor`, `params`, or `return_type`
- current query helpers only build direct method anchors from `ClassName#methodName`
- current export-and-scan helpers scope exported code by method name only
- descriptor-aware disambiguation is therefore a planner and runner concern first

If the planner receives descriptor-aware input that cannot be enforced by the selected version 1 execution path, it must return `unsupported` or `ambiguous` instead of silently dropping precision.

Version 1 enforcement rule:

- planner and runner may preserve descriptor-aware input in normalized data even when the selected helper cannot consume it directly
- they must not claim descriptor-precise execution unless the selected path can enforce or verify that precision
- version 1 will not add a hidden overload-resolution step outside the declared task strategy just to make a relaxed anchor look precise
- if precision cannot be established from the provided anchor and the declared execution path, the result must stay `ambiguous` or `unsupported`
- for `trace_callers` and `trace_callees`, a descriptor-aware anchor must resolve to exactly one concrete target before relation results are presented as precise
- for `summarize_method_logic`, if the exported class contains multiple overloads with the same method name and the selected path cannot isolate the requested descriptor, the result must be `ambiguous`
- with the current version 1 single-step `run_find` strategy, descriptor-aware relation tracing should normally fail early as `ambiguous` or `unsupported` unless the selected execution path gains descriptor-enforceable matching

Recommended external forms:

- structured form
  - `class_name`
  - `method_name`
  - optional `descriptor`
- compact form
  - `ClassName#methodName`
  - optional future form such as `ClassName#methodName(signature)`

Example shape:

```json
{
  "class_name": "com.example.Target",
  "method_name": "login",
  "descriptor": "(Ljava/lang/String;)Z"
}
```

## Preflight

Before planning, a light preflight stage should validate:

- file existence
- file readability
- input type compatibility
- single-dex requirements for export-based tasks
- presence of required task arguments

Version 1 preflight file-kind rule:

- input-kind detection should be based on explicit path shape and filename extension, not deep content probing
- `.apk` -> APK input candidate
- `.dex` -> dex input candidate
- `.java` or `.smali` -> exported-code input candidate
- unknown extensions should fail preflight unless the task contract explicitly allows them
- mixed APK and dex inputs remain invalid even if every path is individually readable

Preflight is not responsible for business reasoning. It is only responsible for input and environment sanity checks.

## Planner Schema

The planner must emit structured JSON. It must not emit a free-form textual plan as the primary contract.

Required top-level fields:

- `schema_version`
- `planner_version`
- `task_type`
- `inputs`
- `normalized_inputs`
- `steps`
- `limits`
- `expected_outputs`
- `stop_conditions`

Example shape:

```json
{
  "schema_version": "1",
  "planner_version": "1",
  "task_type": "summarize_method_logic",
  "inputs": {
    "input": "/path/to/classes.dex",
    "method_anchor": {
      "class_name": "com.example.Target",
      "method_name": "login"
    }
  },
  "normalized_inputs": {
    "primary_kind": "dex",
    "paths": [
      "/path/to/classes.dex"
    ],
    "path_count": 1
  },
  "steps": [
    {
      "step_id": "step-1",
      "kind": "export_and_scan",
      "tool": "export_and_scan.py",
      "args": {
        "input_dex": "/path/to/classes.dex",
        "class_name": "com.example.Target",
        "method": "login",
        "language": "smali",
        "mode": "summary",
        "output_dir": "/tmp/run-001/exports"
      }
    }
  ],
  "limits": [
    "only direct method body analysis",
    "no automatic resource analysis"
  ],
  "expected_outputs": [
    "structured step result",
    "human-readable summary"
  ],
  "stop_conditions": [
    "stop after the planned steps complete",
    "do not expand into additional hops automatically"
  ]
}
```

## Step Schema

Each plan step should use a constrained schema.

Required fields:

- `step_id`
- `kind`
- `tool`
- `args`

Recommended fields:

- `allow_empty_result`

Artifact-path note:

- if a step is expected to produce artifacts, the plan args should carry explicit runner-owned output locations instead of relying on helper-created temporary directories
- for version 1 this most directly applies to `export_and_scan` steps through `output_dir`

Version 1 step kinds:

- `run_find`
- `export_and_scan`

No dynamic step kinds should be introduced in the first version.

## Runner Rules

The runner is not a second planner.

The runner must:

- consume an existing structured plan
- execute steps in order
- collect structured results
- normalize helper output into stable result payloads
- own subprocess capture
- own the run artifact root and explicit artifact paths passed to helper scripts
- produce one final structured report plus an optional human-readable summary

The runner must not:

- invent new steps
- change the task type
- recursively expand the search without a new plan
- behave as a thin pass-through that only streams helper output

## Step Result Schema

Step results are owned by the runner. Existing helper scripts are execution primitives, not the result contract boundary.

Required fields:

- `step_id`
- `step_kind`
- `status`
- `exit_code`
- `command`
- `stdout`
- `stderr`
- `artifacts`
- `result`

Allowed step `status` values:

- `ok`
- `empty`
- `execution_error`

Status semantics:

- `ok`: the step executed successfully and produced a non-empty normalized result
- `empty`: the step executed successfully but produced no hits or no relevant items
- `execution_error`: the planned command or helper failed

Runner capture rule:

- the runner must capture stdout, stderr, exit code, and the executed command
- the runner must normalize helper output into the step result schema
- helper scripts do not need to emit the final step result schema directly in version 1

Runner normalization rule:

- helper output field names are not the public contract boundary
- the runner owns the normalized field naming used in plan results and final analysis results
- version 1 normalized result keys should use one naming convention consistently
- if an existing helper emits camelCase keys such as `exportPath`, `branchLineCount`, `methodCalls`, or `fieldAccesses`, the runner must map them explicitly instead of leaking mixed naming styles into the final contract

Step-status boundary rule:

- `ambiguous` and `unsupported` are run-level outcomes, not step-level statuses in version 1
- a step may still be `ok` or `empty` even when the final run status becomes `ambiguous` after the runner evaluates the normalized results against task precision requirements
- if the runner can determine `unsupported` before any planned step executes, it should return a run-level `unsupported` result without fabricating step results

Example shape:

```json
{
  "step_id": "step-1",
  "step_kind": "run_find",
  "status": "ok",
  "exit_code": 0,
  "command": [
    "python3",
    "./skills/dexclub-cli-launcher/analyst/scripts/run_find.py",
    "method",
    "--input",
    "/path/to/app.apk",
    "--using-string",
    "login"
  ],
  "stdout": "[...]",
  "stderr": "",
  "artifacts": [],
  "result": {
    "count": 3,
    "items": []
  }
}
```

## Normalized Result Payloads

The runner should normalize step payloads into a small set of result shapes instead of leaving `step_results[].result` open-ended.

### Common hit-list result

Used for direct query-driven steps.

Required fields:

- `count`
- `items`

Recommended fields:

- `truncated`

Field semantics:

- `count` is the number of normalized `items` returned in this payload
- `truncated = true` means additional matching items are known or strongly implied to exist beyond the returned payload
- version 1 does not require a separate `total_count` field because the current CLI path does not expose an exact pre-limit total
- `truncated = true` may be caused by threshold overflow, external `limit`, or both

Common hit item fields when available:

- `class_name`
- `method_name`
- `descriptor`

Recommended item fields when available:

- `source_dex_path`

If a source does not provide one of these fields, the runner may omit it, but it must not silently rename equivalent data to a different field name.

### Shared relation result

`trace_callers` and `trace_callees` must share one relation result schema.

This schema is a specialization of the common hit-list result. It keeps the same `count` and `items` structure, then adds relation-specific context.

Required top-level fields:

- `relation_direction`
- `anchor`
- `count`
- `items`

Required item fields:

- `class_name`
- `method_name`

Recommended item fields:

- `descriptor`
- `source_dex_path`

Example shape:

```json
{
  "relation_direction": "callers",
  "anchor": {
    "class_name": "com.example.Target",
    "method_name": "login",
    "descriptor": "(Ljava/lang/String;)Z"
  },
  "count": 2,
  "items": [
    {
      "class_name": "com.example.EntryActivity",
      "method_name": "onClick",
      "descriptor": "(Landroid/view/View;)V"
    }
  ]
}
```

### Export-and-scan result

Used by `export_and_scan` based steps.

Required fields:

- `export_path`
- `kind`
- `scope`

Recommended fields:

- `branch_line_count`
- `return_line_count`
- `strings`
- `numbers`
- `method_calls`
- `field_accesses`

Normalization note:

- current helper output uses camelCase keys such as `exportPath`, `branchLineCount`, `returnLineCount`, `methodCalls`, and `fieldAccesses`
- version 1 runner output must normalize these into the schema key names defined here

## Analysis Result Schema

The runner must emit one final structured result for the whole run.

Required top-level fields:

- `schema_version`
- `run_id`
- `status`
- `task_type`
- `artifact_root`
- `plan`
- `step_results`
- `summary`
- `artifacts`
- `recommendations`

Recommended top-level fields:

- `started_at`
- `finished_at`
- `limits`
- `evidence`

Allowed run `status` values:

- `ok`
- `empty`
- `input_error`
- `execution_error`
- `unsupported`
- `ambiguous`

Status semantics:

- `ok`: execution succeeded and produced non-empty results
- `empty`: execution succeeded but produced no hits
- `input_error`: required input or preflight validation failed
- `execution_error`: a planned tool or command failed
- `unsupported`: the task type or input combination is outside version 1 support
- `ambiguous`: multiple candidates were found when a precise target was required

Example shape:

```json
{
  "schema_version": "1",
  "run_id": "2026-04-08T02-00-00Z-001",
  "status": "ok",
  "task_type": "search_methods_by_string",
  "artifact_root": "/tmp/run-001",
  "plan": {},
  "step_results": [],
  "summary": {
    "text": "Found 3 matching methods.",
    "style": "partial_support"
  },
  "artifacts": [],
  "limits": [
    "only direct query matching"
  ],
  "evidence": [],
  "recommendations": []
}
```

### Summary schema

Required fields:

- `text`
- `style`

Recommended fields:

- `highlights`

### Recommendation schema

Use this when the planner or runner needs to suggest a narrower follow-up instead of expanding automatically.

Required fields:

- `kind`
- `message`

Recommended fields:

- `reason`
- `suggested_filters`

Example shape:

```json
{
  "kind": "narrow_search",
  "message": "Too many direct hits. Narrow by package or declaring class.",
  "reason": "max_direct_hits_exceeded",
  "suggested_filters": {
    "search_package": [
      "com.example.feature"
    ],
    "declared_class": "com.example.Target"
  }
}
```

### Evidence schema

Evidence must be machine-readable and traceable to steps and artifacts.

Required fields:

- `step_id`
- `kind`
- `value`

Recommended fields:

- `source_path`
- `line_numbers`
- `notes`

Example shape:

```json
{
  "step_id": "step-1",
  "kind": "string_hit",
  "value": "hello-needle",
  "source_path": "/tmp/run-001/exports/Target.smali",
  "line_numbers": [
    47
  ],
  "notes": "Observed in the exported method body."
}
```

Evidence in the final result should make it possible to answer:

- which step produced the hit
- which class or method matched
- which exported file was analyzed
- which strings, numbers, or method calls were observed

## Artifact Rules

Artifact handling must be explicit from the first implementation.

Required rules:

- All generated artifacts must live under a predictable temporary root.
- Exported files must be referenced in structured output.
- The runner must not hide artifact locations.
- Temporary directories may be retained by default in early versions for easier debugging.

Required artifact fields:

- `type`
- `path`
- `produced_by_step`

Reserved artifact `type` values for version 1:

- `run_root`
- `exported_code`
- `step_result`

Run-level artifact layout:

- `<tmp-root>/<run-id>/`
- `<tmp-root>/<run-id>/exports/`
- `<tmp-root>/<run-id>/results/`

Version 1 rules:

- the runner owns this layout
- the runner should pass explicit output paths or output directories to helper scripts whenever artifacts are expected
- keep artifacts by default
- surface the run artifact root through top-level `artifact_root`
- avoid hidden cleanup until artifact usage is stable

Step-to-step data rule:

- a later step may reference artifact paths produced by an earlier step
- a later step must not mutate or reinterpret a previous step result in place

## Failure Semantics

Different failure modes must be distinguished clearly.

### `input_error`

Use `input_error` when the failure is about malformed, missing, unreadable, or internally inconsistent input data itself.

Examples:

- missing required argument
- unreadable input
- invalid input type
- missing `method_anchor` for a method task
- non-existent file path

### `empty`

Use `empty` when execution succeeded but produced zero hits.

### `execution_error`

Use `execution_error` when a planned tool or command failed.

Examples:

- CLI failure
- export failure
- helper-script failure

The runner should preserve the command, stderr, and current artifact state. If stdout contains partial machine-readable output before the failure, the runner may retain it as diagnostic context.

### `unsupported`

Use `unsupported` when the request is well-formed but outside version 1 capability or task/input support policy.

Examples:

- `summarize_method_logic` with APK input
- any task with external `exported_code` input
- a request outside the initial task set

### `ambiguous`

Use `ambiguous` when the target is not precise enough for the selected execution path.

Examples:

- a method anchor resolves to multiple overloads
- the candidate result set is too broad for a task that requires a precise target

## Task Strategies

Version 1 should map task types to fixed strategies.

### `search_methods_by_string`

Steps:

- one `run_find` step

Matcher and target:

- target: `method`
- key matcher: `usingStrings`

Input limits:

- accepted input kinds are defined by the version 1 task and input matrix

Semantic limits:

- do not auto-expand when `max_direct_hits` is exceeded
- threshold detection should use an internal over-fetch window instead of trusting a helper-reported total count that does not currently exist
- if overflow is detected, version 1 may return a truncated preview plus recommendation, but it must not present that preview as the complete hit set

Result shape:

- common hit-list result

### `search_methods_by_number`

Steps:

- one `run_find` step

Matcher and target:

- target: `method`
- key matcher: `usingNumbers`

Input limits:

- accepted input kinds are defined by the version 1 task and input matrix

Semantic limits:

- do not auto-expand when `max_direct_hits` is exceeded
- threshold detection should use an internal over-fetch window instead of trusting a helper-reported total count that does not currently exist
- if overflow is detected, version 1 may return a truncated preview plus recommendation, but it must not present that preview as the complete hit set

Result shape:

- common hit-list result

### `summarize_method_logic`

Steps:

- one `export_and_scan` step

Matcher and target:

- default export language: `smali`
- default scan mode: `summary`

Input limits:

- accepted input kinds are defined by the version 1 task and input matrix
- requires exactly one dex path

Semantic limits:

- scans one exported class and an optional method-name scope
- provides direct body-level analysis only
- if the method anchor cannot be resolved precisely enough for this path, return `ambiguous`
- descriptor-aware requests must not be silently downgraded to method-name-only claims
- version 1 does not insert an extra hidden overload-resolution step beyond the declared `export_and_scan` strategy

Result shape:

- export-and-scan result

### `trace_callers`

Steps:

- one `run_find` step

Matcher and target:

- target: `method`
- key matcher: `invokeMethods`

Input limits:

- accepted input kinds are defined by the version 1 task and input matrix

Semantic limits:

- only direct callers in version 1
- output items are candidate caller methods of the anchor
- `relation_direction = "callers"`
- this is matcher-constrained direct relation search, not a full call graph expansion
- descriptor-aware requests require exact target resolution before precise caller reporting
- version 1 does not insert a separate hidden resolution step beyond the declared `run_find` strategy

Result shape:

- shared relation result

### `trace_callees`

Steps:

- one `run_find` step

Matcher and target:

- target: `method`
- key matcher: `callerMethods`

Input limits:

- accepted input kinds are defined by the version 1 task and input matrix

Semantic limits:

- only direct callees in version 1
- output items are candidate callee methods constrained by the anchor
- `relation_direction = "callees"`
- this is matcher-constrained direct relation search, not a full call graph expansion
- descriptor-aware requests require exact target resolution before precise callee reporting
- version 1 does not insert a separate hidden resolution step beyond the declared `run_find` strategy

Result shape:

- shared relation result

## Output Contract

Each high-level analysis run should support two output layers:

1. structured output
   - plan
   - step results
   - artifact paths
   - machine-readable hits and summaries
2. human-readable output
   - concise summary
   - key evidence
   - explicit limits

Version 1 command contract:

- `analyze.py` stdout should be machine-readable by default
- JSON is the default and primary stdout contract
- if human-readable output is needed, expose it through an explicit output mode instead of mixing JSON and prose on the default stdout channel

Version 1 CLI shape:

- use explicit subcommands rather than one overloaded free-form entrypoint
- recommended top-level form:
  - `analyze.py plan --task-type ... --input-json ...`
  - `analyze.py run --task-type ... --input-json ...`
- if a compact task-specific form is added later, it must remain a thin compatibility layer above the same planner and runner contract

Recommended examples:

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py plan \
  --task-type search_methods_by_string \
  --input-json '{"input":["/path/to/classes.dex"],"string":"login"}'
```

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py run \
  --task-type summarize_method_logic \
  --input-json '{"input":["/path/to/classes.dex"],"method_anchor":{"class_name":"com.example.Target","method_name":"login"}}'
```

Minimum example expectations:

- the documentation should eventually include one concrete final-result JSON example for:
  - `search_methods_by_string`
  - `trace_callers`
  - `summarize_method_logic`
- those examples should be based on observed helper output, not invented field names beyond the normalized schema

## Recommended File Placement

The first implementation should use:

- `skills/dexclub-cli-launcher/analyst/scripts/plan_schema.py`
- `skills/dexclub-cli-launcher/analyst/scripts/planner.py`
- `skills/dexclub-cli-launcher/analyst/scripts/runner.py`
- `skills/dexclub-cli-launcher/analyst/scripts/analyze.py`

## Validation Plan

Version 1 should define a minimum validation bar before the planner layer is treated as stable:

1. syntax validation
   - run `python3 -m py_compile` against new analyst scripts
2. planner contract validation
   - confirm `plan` output is valid JSON and does not mix prose into stdout
3. runner contract validation
   - confirm `run` output is valid JSON and includes `plan`, `step_results`, `summary`, and `artifact_root`
4. task coverage validation
   - run one small end-to-end sample for each of the five supported task types
5. threshold validation
   - confirm direct-search tasks detect overflow through the internal over-fetch window and emit narrowing recommendations
6. ambiguity validation
   - confirm overload-ambiguous method targets return `ambiguous` instead of silently choosing one candidate

Validation artifacts should be retained during early versions so failed runs can be inspected without rerunning everything.

## First Implementation Order

Recommended order:

1. `plan_schema.py`
2. `planner.py`
3. `runner.py`
4. `analyze.py`
5. analyst documentation updates
6. end-to-end validation on a small dex sample

## Decisions Fixed For Version 1

- Use a normalized input model.
- Use a normalized method anchor model.
- Use `input` as the external task field name across version 1 tasks.
- Emit one final analysis result object per run.
- Surface the run artifact root through top-level `artifact_root`.
- Normalize threshold follow-up guidance through top-level `recommendations`.
- Keep artifacts by default.
- Return `ambiguous` instead of guessing when a method target is not precise enough.
- Use `partial_support` wording instead of `best_effort`.
- Use explicit subcommands for `analyze.py` in version 1.
- Use one shared relation result schema for `trace_callers` and `trace_callees`, distinguished by `relation_direction`.

## Current Recommendation

Proceed with a constrained first implementation.

Do not expand task scope before:

- the schema is stable
- step result handling is stable
- artifact handling is predictable
- the first five task types behave consistently

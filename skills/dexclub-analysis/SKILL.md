---
name: dexclub-analysis
description: Use when Codex needs to analyze APK, Dex, manifest, resources, classes, fields, or methods through dexclub MCP, especially for black-box Android reverse-engineering, feature location, implementation tracing, and competitor analysis. This skill runs only when `mcp__dexclub__` is available at execution time.
metadata:
  dexclub_mcp_version: "__DEXCLUB_VERSION__"
  dexclub_mcp_contract_version: "__DEXCLUB_MCP_CONTRACT_VERSION__"
---

# DexClub Analysis

## Overview

Drive `mcp__dexclub__` as the primary APK, Dex, manifest, and resource analysis surface. Keep analysis iterative: locate the smallest useful candidate set, inspect one-layer facts, export implementation text only when needed, then report evidence and uncertainty.

## Hard Gate

Before analysis, confirm that `mcp__dexclub__` is available in the current tool list. Then call
`get_server_info` before any other DexClub tool and compare its `version` and
`mcp_contract_version` with this skill's `dexclub_mcp_version` and
`dexclub_mcp_contract_version` metadata.

If the MCP is unavailable, `get_server_info` is unavailable, or either version differs:

- stop
- tell the user the DexClub MCP and skill must come from the same release
- ask them to install or reconnect the matching release

Do not fall back to shell reverse engineering, local decompiled output, or dexclub CLI. This skill is MCP-first.
Do not copy the version returned by the server into later calls to bypass a mismatch. Every DexClub
business tool call must include `version` set to this skill's `dexclub_mcp_version` metadata value.

## Default Workflow

Use this order unless the task clearly justifies a shorter path:

1. call `open_target_session` with the absolute target path in `input`
2. choose one entry path from the strongest clue
3. run the smallest useful `find_*`, manifest, or resource query
4. use `brief=true` first and project extra `fields` only when needed
5. inspect one likely method before exporting code
6. export only the evidence text needed to resolve a concrete uncertainty
7. summarize conclusion, evidence, and remaining uncertainty

If the user provides a full method descriptor such as `Lpkg/Class;->name(args)Ret`, skip search:

- open a target session
- call `inspect_method` with `descriptor`
- export that method only when implementation text is required

Treat a full descriptor as a direct object reference, not a search hint.

When calling `open_target_session`, pass the absolute existing target path in the `input` argument.
Do not use a `path` argument. Relative paths resolve against the MCP server process, not the
conversational working directory.

Interpret snapshot `inventoryCounts` as recognized top-level target materials, not archive members.
For an APK, `apkCount=1` with zero Dex, Manifest, or ARSC counts is expected even when those
capabilities work through entries inside the APK. Use `capabilities` and focused tool calls to
verify supported operations; do not report the top-level counts as an extraction mismatch.

## Route by Clue

Choose the entry tool by clue type instead of following one global tool priority.

- exact method descriptor: `inspect_method`
- code string or literal: `find_methods` with `query.matcher.usingStrings`
- class-level string or class structure: `find_classes`
- class or method name: `find_classes` or `find_methods` with the corresponding matcher
- field name, type, owner, reader, or writer: `find_fields`
- caller or callee relationship: nested `callerMethods` or `invokeMethods` in `find_methods`
- manifest component, permission, or intent filter: `manifest`; narrow one component with `component_name` and `component_type`
- resource name or identity: `list_res`
- concrete resource value: `get_resource_value`
- decoded, raw, reference, or bag-item resource value clue: `find_resource_values`
- resource ID usage in Dex: convert the hexadecimal resId to its signed 32-bit decimal value, then use `find_methods` with `query.matcher.usingNumbers[].intValue`

Do not start several broad paths in parallel. Test the strongest clue first, then backtrack when that path stops adding facts.

After two or three broad searches or narrowing steps in one branch, pause and state internally:

- what the branch established
- what remains unknown
- why the next query should add a new fact

If there is no clear answer, switch clues instead of repeating nearby keywords.

## Find Query Contract

Use only these unified Dex search tools:

- `find_classes`
- `find_methods`
- `find_fields`

For each tool:

- pass `query` as a required JSON object, never a JSON string
- construct `query` from the exact recursive schema advertised by the tool
- keep root filters such as `searchPackages`, `excludePackages`, `ignorePackagesCase`, and `findFirst` inside `query`
- keep structural conditions under `query.matcher`
- never pass `searchInClasses`, `searchInMethods`, or `searchInFields`
- never use BatchFind or removed using-strings tools
- never use legacy flattened inputs such as `class_name_contains`, `method_name_contains`, or `descriptor_contains`

Read [references/find-queries.md](references/find-queries.md) whenever constructing, combining, or repairing a common `find_*` query. Read [references/find-query-fields.md](references/find-query-fields.md) when an exact field, nested type, required property, default, or enum value is unclear. Treat the live MCP tool schema as authoritative; do not copy or infer fields that it does not advertise.

Read the required query reference before the first `find_*` call. A rejected exploratory query is
a failed attempt, not a valid endpoint check, even when a corrected retry later succeeds.

Start recursive relationship matchers one layer deep. Add another layer only when it tests a concrete hypothesis and materially narrows candidates.

Avoid an empty `query` or empty `matcher` unless the user explicitly requests inventory-like enumeration. Prefer adding a real package, string, name, type, annotation, field, caller, or callee constraint.

Use `findFirst=true` only for existence checks or a target expected to be unique. Do not use it during candidate discovery when ranking or ambiguity matters.

## Session Rules

Prefer target-session-first analysis.

- open a session unless the task is a one-shot light query
- keep using `session_id` after opening it
- stop passing `workdir` once `session_id` exists
- prefer returned `method_handle` and `class_handle`
- use `method_handle` / `class_handle` as tool inputs
- use `methodHandle` / `classHandle` as projected result fields
- never invent a bare `handle` field

If the underlying target changes, call `refresh_target_session`, discard old handles, and reacquire them through fresh results.

After restoring a chat or restarting Codex or the MCP server, confirm previous state with `get_target_session`, `list_target_sessions`, or `diagnose_target_sessions`. Reopen the target when the session no longer exists.

If a session or handle is not found, rebuild or reacquire it. Never reconstruct handles manually.

Require every inspect or export handle to be a non-empty value copied from a successful result in
the current session. When a handle call returns not found, discard that handle, rerun the narrow
lookup, and use the newly returned handle. Never retry an unchanged missing handle.

For a bounded one-shot validation, close the target session before the final report and use
`diagnose_target_sessions` or `list_target_sessions` when cleanup itself is part of the check.
If an interactive follow-up is clearly expected, a session may remain open for reuse, but state
that it is intentionally retained and do not create additional sessions for the same target.

Do not send the final report until one of those two end states is true: the session was closed and
cleanup was verified, or the report explicitly says that the session remains open for follow-up.

## Result and Paging Discipline

Use `brief=true` for initial `find_*`, `list_res`, and `find_resource_values` calls unless detail is required.

- omit `fields` on the first compact session-based lookup when brief defaults are sufficient
- request only fields needed by the next step
- do not request `methodHandle` or `classHandle` without `session_id`
- use a smaller explicit `limit` for weak or broad clues
- otherwise accept the default `limit=50`; never exceed 200

Treat every page as a result window, not a representative sample. Do not assume result order expresses relevance.

When `hasMore=true`:

- do not infer absence, uniqueness, prevalence, or the most likely implementation from the current window
- choose explicitly between independent-clue narrowing, exhaustive coverage, and stratified exploration
- narrow only with evidence independent of superficial similarity in the current window
- report incomplete coverage as `examined/total` when stopping before `hasMore=false`

Use exhaustive coverage for a manageable result set: keep the same `session_id`, `query`, projection, and target snapshot; use `brief=true` with `limit=200`; advance `offset` until `hasMore=false`; inspect only the resulting shortlist. For a result set too large to exhaust, sample separated offsets only to discover clusters and better Matcher constraints. Never use sampling to prove a negative or complete result.

Read the paging modes and stopping rules in [references/find-queries.md](references/find-queries.md) before drawing conclusions from a truncated `find_*` result.

Use only these result projection fields:

- `find_methods`: `className`, `methodName`, `descriptor`, `sourcePath`, `sourceEntry`, `methodHandle`
- `find_classes`: `className`, `sourcePath`, `sourceEntry`, `classHandle`
- `find_fields`: `className`, `fieldName`, `descriptor`, `sourcePath`, `sourceEntry`

Do not guess aliases such as `name`, `typeName`, or `handle`.

Once a handle exists, stop repeating descriptor and source constraints unless disambiguation requires them. Do not synthesize descriptors from Java output or obfuscated guesses; reacquire exact descriptors from MCP results.

## Manifest Rules

Default to structured manifest inspection.

- omit `include_text` unless raw XML is evidence
- constrain `include` when only selected sections matter
- pass `component_name` and `component_type` when inspecting one component; do not fetch every component and filter client-side
- use only schema-advertised include values

Common manifest sections include `uses-sdk`, `application`, `uses-permissions`, `defined-permissions`, `uses-features`, `queries`, `activities`, `activity-aliases`, `services`, `receivers`, and `providers`.

For launcher discovery, query `activity-aliases` first and follow the returned target activity with
a component-scoped `manifest` call. Only request the full `activities` section when no usable alias
exists or the launcher target is unresolved; do not fetch all activities and aliases together when
a staged query can answer the same question.

Application and component results expose high-value fields such as `theme` and `windowSoftInputMode` plus a namespace-aware `attributes` list. Use the explicit field when it answers the question; inspect `attributes` for long-tail or same-local-name attributes without losing namespace identity. Preserve raw resource references from attributes and resolve them through `get_resource_value`.

## Resource Semantics

Use `list_res` for resource identity, source mapping, and resolution state. Do not treat it as a decoded-value API.

Interpret resolution values carefully:

- `table-backed`: a packaged file-backed resource
- `table-value`: a usable table value; null `filePath` and `sourceEntry` are normal
- `table-hole`: an empty table slot, not a parser failure
- `unresolved`: incomplete evidence that may require a narrower follow-up

For value analysis:

- use `get_resource_value` for one concrete resource
- use `find_resource_values` for decoded values, raw data, references, and bag keys across every configuration
- use `list_res` to distinguish file-backed, table-valued, and table-hole entries
- use `resource_id` whenever available; otherwise use `package_name`, `resource_type`, and `name` to preserve identity
- when a name lookup is ambiguous, read `error.details.candidates` and retry with a candidate `resourceId`
- use `qualifier` to select one configuration or `include_all_variants=true` to request the complete configuration set
- expect `variants[].value` for scalar/reference values and `variants[].bag` for style, array, attribute, plurals, and unknown compound values

`list_res` supports exact `resource_id`, `package_name`, `resource_type`, `name`, `file_path`, and `resolution` filters. Apply the strongest known identity filters before paging.

Deduplicate resource IDs collected from code before lookup, and resolve only the IDs needed for the
current uncertainty or final evidence. Do not enumerate every nearby integer constant or repeat an
exact `list_res` request already answered in the same target snapshot.

`find_resource_values` separates resource identity from value matching:

- `resource_type` selects the Android resource type
- `value_kind` selects the encoded value kind
- `match_target` selects `decoded_value`, `raw_data`, `reference`, `bag_key`, or `any`
- `qualifier` limits matches to one configuration
- results can project `qualifier`, `valueKind`, `matchTarget`, `bagIndex`, and `bagKey` to locate the exact match

For a resource-to-code chain, preserve the hexadecimal resource ID from Manifest, XML, `list_res`, or a typed reference. Resolve variants or bag items first, then convert that ID to signed 32-bit decimal and search Dex with `find_methods.query.matcher.usingNumbers[].intValue`.

Use only schema-advertised resource projection fields. Common fields are:

- `find_resource_values`: `resourceId`, `packageName`, `type`, `name`, `value`, `qualifier`, `valueKind`, `matchTarget`, `bagIndex`, `bagKey`, `sourcePath`, `sourceEntry`
- `list_res`: `resourceId`, `packageName`, `type`, `name`, `filePath`, `sourcePath`, `sourceEntry`, `resolution`

When reporting tool coverage, derive the inventory from the live `mcp__dexclub__` tool list and
deduplicate names before counting. Report endpoint coverage separately from invocation attempts:
retries of one failing endpoint do not create additional covered tools.

For endpoint validation, compute the inventory once and retain it instead of recounting names in
the final response:

```javascript
const dexclubTools = [...new Set(
  ALL_TOOLS.map(({ name }) => name)
    .filter(name => name.startsWith("mcp__dexclub__")),
)].sort();
```

Use `dexclubTools.length` as the denominator. Track every endpoint by name with successful and
failed attempt counts. Treat an MCP result whose content reports `isError` or contains an `error`
object as a failed attempt even when the outer orchestration call completed successfully. Report
`successful endpoints / dexclubTools.length` separately from total successful and failed attempts.

When validating `decode_xml`, first obtain a small file-backed XML path through `list_res` and
`get_resource_value`. Do not decode `AndroidManifest.xml` merely to exercise the endpoint; use it
only when its raw text is required evidence.

## Inspect and Export Rules

Default to locate, inspect, then export.

Before another export, broad search, or major branch switch, identify the exact uncertainty the step should resolve.

Default per-round budgets:

- export no more than one or two methods
- export no more than one class

Exceed a budget only when the next export tests a key branch, adds a new evidence type, or compensates for incomplete Java or inspect output.

At the end of each evidence branch, synthesize what the current exports prove and name the exact
remaining uncertainty before exporting again. Continue only when another export should resolve that
uncertainty or test a materially different branch. Stop when the user's question is already answered;
do not start a new round merely to inspect nearby candidates.

Prefer Java for quick semantic understanding. Use smali when Java is incomplete, misleading, or insufficient for control-flow proof. Do not export both views without a concrete reason.

For `export_method_smali`, omit `mode` unless needed. Supported modes are `snippet` and `class`.

When several candidates point to one owner class, consider one class export instead of many sibling method exports, but only when class context answers the remaining question.

## Error Recovery

Treat missing sessions, missing handles, unsupported projections, invalid query fields, and invalid enum values as recoverable parameter or context errors.

Recover in this order:

1. determine whether context was lost or the request violates the current schema
2. rebuild the session or reacquire handles for context loss
3. inspect the live tool schema and repair `query`, `fields`, or `include`
4. retry through MCP

If a recursive query is rejected, remove guessed fields and reduce it to the smallest valid matcher, then add constraints back one at a time. Do not switch to shell or CLI because of a recoverable MCP error.

## Output Discipline

Distinguish clues, facts, evidence, and conclusions. Do not promote one search hit directly into a final conclusion.

Keep the scope of a conclusion no broader than the evidence examined. Treat a zero result from a package-, type-, API-, or branch-constrained query as scoped evidence, not a global negative. Before claiming that a feature or flow is absent, follow plausible indirection or explicitly report the inspected scope and remaining uncertainty.

Do not assign semantics to an obfuscated or wrapper call from its name or argument values alone. Inspect the implementation that gives those values meaning, or report the call only as a clue.

Separate discovered candidates from implementation-verified candidates when a batch inspect or
export partly fails. Do not generalize behavior proven by successful items to failed or uninspected
siblings; reacquire and verify them, or report them only as candidates.

Do not present sibling order, ownership, or a fixed layout tree as established when only module
registration and dynamic binding are known. Render unverified siblings as unordered or explicitly
label the ordering as unresolved.

If the user asks only for a likely implementation owner or entry location, stop when evidence is sufficient for that narrower question and state remaining uncertainty.

End an analysis round with:

1. current conclusion
2. key supporting evidence
3. remaining uncertainty

## Useful MCP Surface

Version discovery tool:

- `get_server_info`

Core session tools:

- `open_target_session`
- `list_target_sessions`
- `get_target_session`
- `close_target_session`
- `refresh_target_session`
- `diagnose_target_sessions`

Core analysis tools:

- `manifest`
- `list_res`
- `find_resource_values`
- `get_resource_value`
- `decode_xml`
- `find_classes`
- `find_methods`
- `find_fields`
- `inspect_method`
- `export_class_java`
- `export_class_smali`
- `export_method_java`
- `export_method_smali`

Use `diagnose_target_sessions` whenever session state feels unclear or stale.

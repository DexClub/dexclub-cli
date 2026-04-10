# Analyze V1 Examples

This page records observed version 1 `analyze.py run` results from the analyst layer.

These examples were captured against:

- sample APK:
- `<sample-apk-path>`
- sample dex for `MainActivity#onCreate`:
  - discovered by `scripts/validate_v1_sample.sh` from the APK's extracted `classes*.dex`

Notes:

- `run_id`, `artifact_root`, and export paths vary by machine and by run.
- `analyze.py run` now writes per-run artifacts under `<workspace>/.dexclub-cli/runs/v1/<run-id>/`.
- `analyze.py run` may reuse prior `run_find` / `resolve_apk_dex` / `export_and_scan` step results through `<workspace>/.dexclub-cli/runs/v1/reusable-step-index-v1.json`.
- The reusable step keys are also isolated by the selected `release_tag`, so switching CLI releases does not reuse older step results.
- APK-backed summarize now reuses `<workspace>/.dexclub-cli/cache/v1/inputs/apk/<input-hash>/extracted-dex/` for resolved `classes*.dex`.
- `resolve_apk_dex` now keeps `class-dex-index-v1.json` under the APK cache directory and may report `cache_hit`, `lookup_strategy`, and `scanned_dex_count`.
- `export_and_scan.py` now keeps derived export/scan cache entries under `<workspace>/.dexclub-cli/cache/v1/export-and-scan/<dex-sha256>/<request-hash>/` and may report `cacheHit` / `cachePath` on direct helper runs.
- The export/scan cache is keyed by dex content plus export/scope arguments, so APK-extracted dex and direct dex inputs can converge on the same cached result.
- Reused run steps include `reused_from` metadata in `step_results[]`, while the current run still gets its own `step-result.json` and step-local export artifact path.
- `analyze.py run` also exposes top-level `reused_step_count`, `reused_step_kinds`, and `cache_hit_count` so callers do not need to rescan `step_results[]` just to summarize reuse/cache behavior.
- `analyze.py run` now also fixes the task-level reasoning buckets under `verifiedFacts`, `inferences`, `unknowns`, and `nextChecks`.
- The same reuse/cache counters are also mirrored into `run-summary.json` and `latest.json` for runs-root inspection.
- `analyze.py cache inspect` now also exposes `latest_run`, derived from `latest.json` plus `run-summary.json`, for lightweight recent-run inspection.
- `analyze.py runs latest|inspect|list` now exposes the persisted run projection directly, without going through cache inspection output.
- Invalid reusable-step-index entries are pruned automatically when later runs touch the index.
- `analyze.py cache inspect|prune|clear` now exposes the managed cache roots and index maintenance path directly from the stable entry point.
- `stdout` in `step_results[]` is preserved exactly as captured from helper scripts, including current DexKit info lines.
- For full current sample outputs, run `scripts/validate_v1_sample.sh`; it keeps the JSON files under its reported temporary `results_dir`.
- `summarize_method_logic` accepts either one direct dex input or one APK input. When the input is an APK, the planner inserts a `resolve_apk_dex` step before export.
- Descriptor-aware anchors may use a full descriptor or a signature-only suffix such as `"(Landroid/os/Bundle;)V"` when `class_name` and `method_name` are also present.
- The Java exact-anchor example below is now valid against the published release path.

## Reuse / Cache Counter Contract

`analyze.py run` top-level counters:

```json
{
  "reused_step_count": 2,
  "reused_step_kinds": [
    "resolve_apk_dex",
    "export_and_scan"
  ],
  "cache_hit_count": 0
}
```

Meaning:

- `reused_step_count`: count of `step_results[]` items with `reused_from`
- `reused_step_kinds`: deduplicated reused step kinds, ordered by first appearance in `step_results[]`
- `cache_hit_count`: count of step results whose normalized `result.cache_hit` is `true`

`run-summary.json` and `latest.json` mirror the same three fields:

```json
{
  "run_id": "<run-id>",
  "status": "ok",
  "reused_step_count": 2,
  "reused_step_kinds": [
    "resolve_apk_dex",
    "export_and_scan"
  ],
  "cache_hit_count": 0
}
```

`analyze.py cache inspect --format json` exposes the recent-run projection under `latest_run`:

```json
{
  "latest_run": {
    "run_id": "<run-id>",
    "task_type": "summarize_method_logic",
    "status": "ok",
    "summary_path": "<workspace>/.dexclub-cli/runs/v1/<run-id>/run-summary.json",
    "summary_text": "Resolved one APK dex and summarized one exported method body.",
    "reused_step_count": 2,
    "reused_step_kinds": [
      "resolve_apk_dex",
      "export_and_scan"
    ],
    "cache_hit_count": 0
  }
}
```

`analyze.py runs latest --format json` and `analyze.py runs inspect --run-id <run-id> --format json` expose the same projection under `run`:

```json
{
  "run": {
    "run_id": "<run-id>",
    "task_type": "summarize_method_logic",
    "status": "ok",
    "summary_path": "<workspace>/.dexclub-cli/runs/v1/<run-id>/run-summary.json",
    "final_result_path": "<workspace>/.dexclub-cli/runs/v1/<run-id>/final_result.json",
    "summary_exists": true,
    "final_result_exists": true,
    "reused_step_count": 2,
    "reused_step_kinds": [
      "resolve_apk_dex",
      "export_and_scan"
    ],
    "cache_hit_count": 0
  }
}
```

Adding `--include-final-result` to `runs inspect` embeds the persisted run payload:

```json
{
  "run": {
    "run_id": "<run-id>",
    "final_result_included": true,
    "final_result": {
      "run_id": "<run-id>",
      "task_type": "summarize_method_logic",
      "cache_hit_count": 0
    }
  }
}
```

`analyze.py runs list --limit 10 --format json` exposes recent persisted runs ordered by `updated_at` descending:

```json
{
  "count": 2,
  "items": [
    {
      "run_id": "<latest-run-id>",
      "task_type": "summarize_method_logic",
      "status": "ok",
      "updated_at": "2026-04-10T00:00:00+00:00",
      "reused_step_count": 0,
      "reused_step_kinds": [],
      "cache_hit_count": 0,
      "is_latest": true
    }
  ]
}
```

## Task-Level Reasoning Buckets

Observed top-level reasoning excerpt:

```json
{
  "verifiedFacts": [
    {
      "text": "Resolved one APK dex and summarized one exported method body. Included a smali block outline.",
      "evidence": [
        {
          "kind": "method_call_hit",
          "source_path": "<workspace>/.dexclub-cli/runs/v1/<run-id>/steps/export_and_scan/artifacts/MainActivity.smali",
          "line_numbers": [34]
        }
      ]
    }
  ],
  "inferences": [
    {
      "text": "The current summary is usable as a working baseline, but overload ambiguity still depends on the relaxed anchor context.",
      "confidence": "medium"
    }
  ],
  "unknowns": [],
  "nextChecks": [
    {
      "text": "Review `structured_summary.focus_snippets` first before reading the full export.",
      "reason": "focus_snippets_available"
    }
  ]
}
```

Meaning:

- `verifiedFacts`: direct observations backed by the current run payload and evidence locators
- `inferences`: constrained interpretations built from those facts
- `unknowns`: open points the current run still does not settle
- `nextChecks`: recommended follow-up checks derived from status, limits, and planner/runtime recommendations

## `summarize_method_logic` with APK input

Command:

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py run \
  --task-type summarize_method_logic \
  --input-json '{"input":["<sample-apk-path>"],"method_anchor":{"class_name":"com.shadcn.ui.compose.MainActivity","method_name":"onCreate"}}'
```

Observed result excerpt:

```json
{
  "status": "ok",
  "task_type": "summarize_method_logic",
  "step_results": [
    {
      "step_kind": "resolve_apk_dex",
      "status": "ok",
      "result": {
        "class_name": "com.shadcn.ui.compose.MainActivity",
        "candidate_dex_paths": [
          "<workspace>/.dexclub-cli/cache/v1/inputs/apk/<input-hash>/extracted-dex/classes4.dex"
        ],
        "resolved_dex_path": "<workspace>/.dexclub-cli/cache/v1/inputs/apk/<input-hash>/extracted-dex/classes4.dex"
      }
    },
    {
      "step_kind": "export_and_scan",
      "status": "ok",
      "result": {
        "kind": "smali",
        "scope": {
          "method": "onCreate"
        },
        "method_call_count": 4,
        "export_path": "<workspace>/.dexclub-cli/runs/v1/<run-id>/steps/step-2/artifacts/com_shadcn_ui_compose_MainActivity.smali"
      }
    }
  ],
  "summary": {
    "text": "Resolved one APK dex and summarized one exported method body.",
    "style": "partial_support"
  }
}
```

## `summarize_method_logic` with APK input and exact Java anchor

Command:

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py run \
  --task-type summarize_method_logic \
  --input-json '{"input":["<sample-apk-path>"],"method_anchor":{"class_name":"androidx.compose.foundation.ImageKt","method_name":"Image","descriptor":"Landroidx/compose/foundation/ImageKt;->Image(Landroidx/compose/ui/graphics/ImageBitmap;Ljava/lang/String;Landroidx/compose/ui/Modifier;Landroidx/compose/ui/Alignment;Landroidx/compose/ui/layout/ContentScale;FLandroidx/compose/ui/graphics/ColorFilter;Landroidx/compose/runtime/Composer;II)V"},"language":"java"}'
```

Observed result excerpt:

```json
{
  "status": "ok",
  "task_type": "summarize_method_logic",
  "step_results": [
    {
      "step_kind": "resolve_apk_dex",
      "status": "ok",
      "result": {
        "class_name": "androidx.compose.foundation.ImageKt",
        "candidate_dex_paths": [
          "<workspace>/.dexclub-cli/cache/v1/inputs/apk/<input-hash>/extracted-dex/classes.dex"
        ],
        "resolved_dex_path": "<workspace>/.dexclub-cli/cache/v1/inputs/apk/<input-hash>/extracted-dex/classes.dex"
      }
    },
    {
      "step_kind": "export_and_scan",
      "status": "ok",
      "result": {
        "kind": "java",
        "scope": {
          "method": "Image",
          "method_descriptor": "Landroidx/compose/foundation/ImageKt;->Image(Landroidx/compose/ui/graphics/ImageBitmap;Ljava/lang/String;Landroidx/compose/ui/Modifier;Landroidx/compose/ui/Alignment;Landroidx/compose/ui/layout/ContentScale;FLandroidx/compose/ui/graphics/ColorFilter;Landroidx/compose/runtime/Composer;II)V",
          "line_count": 16,
          "start_line": 49,
          "end_line": 64
        },
        "method_call_count": 10,
        "branch_line_count": 2,
        "structured_summary": {
          "supported": false
        },
        "export_path": "<workspace>/.dexclub-cli/runs/v1/<run-id>/steps/step-2/artifacts/androidx_compose_foundation_ImageKt.java"
      }
    }
  ],
  "summary": {
    "text": "Resolved one APK dex and summarized one exact method body.",
    "style": "partial_support"
  }
}
```

## `search_methods_by_string`

Command:

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py run \
  --task-type search_methods_by_string \
  --input-json '{"input":["<sample-apk-path>"],"string":"https://github.com/shadcn.png","declared_class":"com.shadcn.ui.compose.showcase.docs.AvatarDocsPageKt"}'
```

Observed result excerpt:

```json
{
  "status": "ok",
  "task_type": "search_methods_by_string",
  "plan": {
    "inputs": {
      "input": [
        "<sample-apk-path>"
      ],
      "string": "https://github.com/shadcn.png",
      "declared_class": "com.shadcn.ui.compose.showcase.docs.AvatarDocsPageKt"
    },
    "normalized_inputs": {
      "primary_kind": "apk",
      "path_count": 1
    }
  },
  "step_results": [
    {
      "step_kind": "run_find",
      "status": "ok",
      "result": {
        "count": 1,
        "items": [
          {
            "class_name": "com.shadcn.ui.compose.showcase.docs.AvatarDocsPageKt",
            "method_name": "<clinit>",
            "descriptor": "Lcom/shadcn/ui/compose/showcase/docs/AvatarDocsPageKt;-><clinit>()V",
            "source_dex_path": "<sample-apk-path>"
          }
        ]
      }
    }
  ],
  "summary": {
    "text": "Found 1 matching methods.",
    "style": "partial_support"
  },
  "evidence": [
    {
      "step_id": "step-1",
      "kind": "method_hit",
      "value": "Lcom/shadcn/ui/compose/showcase/docs/AvatarDocsPageKt;-><clinit>()V",
      "source_path": "<sample-apk-path>"
    }
  ]
}
```

## `trace_callers`

Command:

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py run \
  --task-type trace_callers \
  --input-json '{"input":["<sample-apk-path>"],"method_anchor":{"class_name":"androidx.activity.ComponentActivity","method_name":"onCreate"},"limit":3}'
```

Observed result excerpt:

```json
{
  "status": "ok",
  "task_type": "trace_callers",
  "step_results": [
    {
      "step_kind": "run_find",
      "status": "ok",
      "result": {
        "relation_direction": "callers",
        "anchor": {
          "class_name": "androidx.activity.ComponentActivity",
          "method_name": "onCreate"
        },
        "count": 2,
        "items": [
          {
            "class_name": "androidx.compose.ui.tooling.PreviewActivity",
            "method_name": "onCreate",
            "descriptor": "Landroidx/compose/ui/tooling/PreviewActivity;->onCreate(Landroid/os/Bundle;)V",
            "source_dex_path": "<sample-apk-path>"
          },
          {
            "class_name": "com.shadcn.ui.compose.MainActivity",
            "method_name": "onCreate",
            "descriptor": "Lcom/shadcn/ui/compose/MainActivity;->onCreate(Landroid/os/Bundle;)V",
            "source_dex_path": "<sample-apk-path>"
          }
        ]
      }
    }
  ],
  "summary": {
    "text": "Found 2 direct callers.",
    "style": "partial_support"
  }
}
```

## `trace_callees` with descriptor-aware anchor

Command:

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py run \
  --task-type trace_callees \
  --input-json '{"input":["<sample-apk-path>"],"method_anchor":{"class_name":"com.shadcn.ui.compose.MainActivity","method_name":"onCreate","descriptor":"(Landroid/os/Bundle;)V"},"limit":5}'
```

Observed result excerpt:

```json
{
  "status": "ok",
  "task_type": "trace_callees",
  "plan": {
    "inputs": {
      "method_anchor": {
        "class_name": "com.shadcn.ui.compose.MainActivity",
        "method_name": "onCreate",
        "descriptor": "Lcom/shadcn/ui/compose/MainActivity;->onCreate(Landroid/os/Bundle;)V",
        "params": [
          "android.os.Bundle"
        ],
        "return_type": "void"
      }
    }
  },
  "step_results": [
    {
      "step_kind": "run_find",
      "status": "ok",
      "result": {
        "relation_direction": "callees",
        "anchor": {
          "class_name": "com.shadcn.ui.compose.MainActivity",
          "method_name": "onCreate",
          "descriptor": "Lcom/shadcn/ui/compose/MainActivity;->onCreate(Landroid/os/Bundle;)V"
        },
        "count": 4,
        "items": [
          {
            "method_name": "onCreate",
            "descriptor": "Landroidx/activity/ComponentActivity;->onCreate(Landroid/os/Bundle;)V"
          },
          {
            "method_name": "setContent$default",
            "descriptor": "Landroidx/activity/compose/ComponentActivityKt;->setContent$default(Landroidx/activity/ComponentActivity;Landroidx/compose/runtime/CompositionContext;Lkotlin/jvm/functions/Function2;ILjava/lang/Object;)V"
          }
        ]
      }
    }
  ],
  "summary": {
    "text": "Found 4 direct callees.",
    "style": "partial_support"
  }
}
```

## `summarize_method_logic`

Command:

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py run \
  --task-type summarize_method_logic \
  --input-json '{"input":["<main-activity-dex-path>"],"method_anchor":{"class_name":"com.shadcn.ui.compose.MainActivity","method_name":"onCreate"}}'
```

Observed result excerpt:

```json
{
  "status": "ok",
  "task_type": "summarize_method_logic",
  "step_results": [
    {
      "step_kind": "export_and_scan",
      "status": "ok",
      "result": {
        "kind": "smali",
        "scope": {
          "path": "<workspace>/.dexclub-cli/runs/v1/<run-id>/steps/step-1/artifacts/com_shadcn_ui_compose_MainActivity.smali",
          "method": "onCreate",
          "line_count": 36,
          "start_line": 54,
          "end_line": 89
        },
        "branch_line_count": 0,
        "return_line_count": 1,
        "string_count": 1,
        "number_count": 4,
        "method_call_count": 4,
        "field_access_count": 0,
        "structured_summary": {
          "kind": "smali_block_outline_v1",
          "supported": true,
          "basic_block_count": 1,
          "call_cluster_count": 1,
          "constant_cluster_count": 1,
          "focus_snippet_count": 3
        },
        "large_method_analysis": {
          "is_large_method": false,
          "line_threshold": 120
        },
        "strings": [
          {
            "value": "savedInstanceState",
            "count": 1,
            "lines": [
              56
            ]
          }
        ],
        "numbers": [
          {
            "value": "0x3",
            "count": 1,
            "lines": [
              66
            ]
          },
          {
            "value": "4",
            "count": 3,
            "lines": [
              66,
              68,
              83
            ]
          }
        ],
        "method_calls": [
          {
            "value": "Landroidx/activity/ComponentActivity;->onCreate(Landroid/os/Bundle;)V",
            "count": 1,
            "lines": [
              59
            ]
          },
          {
            "value": "Landroidx/activity/compose/ComponentActivityKt;->setContent$default(Landroidx/activity/ComponentActivity;Landroidx/compose/runtime/CompositionContext;Lkotlin/jvm/functions/Function2;ILjava/lang/Object;)V",
            "count": 1,
            "lines": [
              85
            ]
          }
        ],
        "field_accesses": [],
        "export_path": "<workspace>/.dexclub-cli/runs/v1/<run-id>/steps/step-1/artifacts/com_shadcn_ui_compose_MainActivity.smali"
      }
    }
  ],
  "summary": {
    "text": "Summarized one exported method body. Included a smali block outline. Included focused smali snippets.",
    "style": "partial_support"
  }
}
```

## `summarize_method_logic` with overloaded target

Command:

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py run \
  --task-type summarize_method_logic \
  --input-json '{"input":["<foundation-image-dex-path>"],"method_anchor":{"class_name":"androidx.compose.foundation.ImageKt","method_name":"Image"}}'
```

Observed result excerpt:

```json
{
  "status": "ambiguous",
  "task_type": "summarize_method_logic",
  "step_results": [
    {
      "step_kind": "export_and_scan",
      "status": "ok",
      "result": {
        "kind": "smali",
        "scope": {
          "method": "Image"
        },
        "method_call_count": 9,
        "export_path": "<workspace>/.dexclub-cli/runs/v1/<run-id>/steps/step-1/artifacts/androidx_compose_foundation_ImageKt.smali"
      }
    }
  ],
  "summary": {
    "text": "Exported class contains 3 overloads for `Image`; current path cannot isolate one precisely.",
    "style": "ambiguous"
  },
  "recommendations": [
    {
      "kind": "narrow_search",
      "reason": "overload_ambiguity"
    }
  ],
  "limits": [
    "only direct method body analysis",
    "method anchor does not include a descriptor; overloads may be ambiguous",
    "export-and-scan cannot disambiguate overloaded methods by descriptor"
  ]
}
```

## `summarize_method_logic` with exact overloaded target

Command:

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py run \
  --task-type summarize_method_logic \
  --input-json '{"input":["<foundation-image-dex-path>"],"method_anchor":{"class_name":"androidx.compose.foundation.ImageKt","method_name":"Image","descriptor":"Landroidx/compose/foundation/ImageKt;->Image(Landroidx/compose/ui/graphics/ImageBitmap;Ljava/lang/String;Landroidx/compose/ui/Modifier;Landroidx/compose/ui/Alignment;Landroidx/compose/ui/layout/ContentScale;FLandroidx/compose/ui/graphics/ColorFilter;Landroidx/compose/runtime/Composer;II)V"}}'
```

Observed result excerpt:

```json
{
  "status": "ok",
  "task_type": "summarize_method_logic",
  "step_results": [
    {
      "step_kind": "export_and_scan",
      "status": "ok",
      "result": {
        "kind": "smali",
        "scope": {
          "method": "Image",
          "method_descriptor": "Landroidx/compose/foundation/ImageKt;->Image(Landroidx/compose/ui/graphics/ImageBitmap;Ljava/lang/String;Landroidx/compose/ui/Modifier;Landroidx/compose/ui/Alignment;Landroidx/compose/ui/layout/ContentScale;FLandroidx/compose/ui/graphics/ColorFilter;Landroidx/compose/runtime/Composer;II)V",
          "line_count": 259
        },
        "method_descriptor": "Landroidx/compose/foundation/ImageKt;->Image(Landroidx/compose/ui/graphics/ImageBitmap;Ljava/lang/String;Landroidx/compose/ui/Modifier;Landroidx/compose/ui/Alignment;Landroidx/compose/ui/layout/ContentScale;FLandroidx/compose/ui/graphics/ColorFilter;Landroidx/compose/runtime/Composer;II)V",
        "method_call_count": 9,
        "structured_summary": {
          "kind": "smali_block_outline_v1",
          "supported": true,
          "basic_block_count": 25,
          "call_cluster_count": 4,
          "constant_cluster_count": 2,
          "focus_snippet_count": 6
        },
        "large_method_analysis": {
          "is_large_method": true,
          "line_threshold": 120,
          "group_count": 5,
          "groups": [
            {
              "kind": "method_calls"
            },
            {
              "kind": "branch_hotspots"
            }
          ]
        },
        "export_path": "<workspace>/.dexclub-cli/runs/v1/<run-id>/steps/step-1/artifacts/androidx_compose_foundation_ImageKt.smali"
      }
    }
  ],
  "summary": {
    "text": "Summarized one exact method body. Included a smali block outline. Included focused smali snippets. Attached grouped hotspot compression for the large smali body.",
    "style": "partial_support"
  }
}
```

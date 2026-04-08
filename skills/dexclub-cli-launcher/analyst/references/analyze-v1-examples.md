# Analyze V1 Examples

This page records observed version 1 `analyze.py run` results from the analyst layer.

These examples were captured against:

- sample APK:
  - `/data/data/com.termux/files/home/AndroidProjects/shadcn/app/build/outputs/apk/debug/app-debug.apk`
- sample dex for `MainActivity#onCreate`:
  - discovered by `scripts/validate_v1_sample.sh` from the APK's extracted `classes*.dex`

Notes:

- `run_id`, `artifact_root`, and temporary export paths vary by machine and by run.
- `stdout` in `step_results[]` is preserved exactly as captured from helper scripts, including current DexKit info lines.
- For full current sample outputs, run `scripts/validate_v1_sample.sh`; it keeps the JSON files under its reported temporary `results_dir`.
- `summarize_method_logic` accepts either one direct dex input or one APK input. When the input is an APK, the planner inserts a `resolve_apk_dex` step before export.
- Descriptor-aware anchors may use a full descriptor or a signature-only suffix such as `"(Landroid/os/Bundle;)V"` when `class_name` and `method_name` are also present.

## `summarize_method_logic` with APK input

Command:

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py run \
  --task-type summarize_method_logic \
  --input-json '{"input":["/data/data/com.termux/files/home/AndroidProjects/shadcn/app/build/outputs/apk/debug/app-debug.apk"],"method_anchor":{"class_name":"com.shadcn.ui.compose.MainActivity","method_name":"onCreate"}}'
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
          "/tmp/dexclub-analyst-runs/<run-id>/resolved/step-1/classes4.dex"
        ],
        "resolved_dex_path": "/tmp/dexclub-analyst-runs/<run-id>/resolved/step-1/classes4.dex"
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
        "export_path": "/tmp/dexclub-analyst-runs/<run-id>/exports/com_shadcn_ui_compose_MainActivity.smali"
      }
    }
  ],
  "summary": {
    "text": "Resolved one APK dex and summarized one exported method body.",
    "style": "partial_support"
  }
}
```

## `search_methods_by_string`

Command:

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py run \
  --task-type search_methods_by_string \
  --input-json '{"input":["/data/data/com.termux/files/home/AndroidProjects/shadcn/app/build/outputs/apk/debug/app-debug.apk"],"string":"https://github.com/shadcn.png","declared_class":"com.shadcn.ui.compose.showcase.docs.AvatarDocsPageKt"}'
```

Observed result excerpt:

```json
{
  "status": "ok",
  "task_type": "search_methods_by_string",
  "plan": {
    "inputs": {
      "input": [
        "/data/data/com.termux/files/home/AndroidProjects/shadcn/app/build/outputs/apk/debug/app-debug.apk"
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
            "source_dex_path": "/data/data/com.termux/files/home/AndroidProjects/shadcn/app/build/outputs/apk/debug/app-debug.apk"
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
      "source_path": "/data/data/com.termux/files/home/AndroidProjects/shadcn/app/build/outputs/apk/debug/app-debug.apk"
    }
  ]
}
```

## `trace_callers`

Command:

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py run \
  --task-type trace_callers \
  --input-json '{"input":["/data/data/com.termux/files/home/AndroidProjects/shadcn/app/build/outputs/apk/debug/app-debug.apk"],"method_anchor":{"class_name":"androidx.activity.ComponentActivity","method_name":"onCreate"},"limit":3}'
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
            "source_dex_path": "/data/data/com.termux/files/home/AndroidProjects/shadcn/app/build/outputs/apk/debug/app-debug.apk"
          },
          {
            "class_name": "com.shadcn.ui.compose.MainActivity",
            "method_name": "onCreate",
            "descriptor": "Lcom/shadcn/ui/compose/MainActivity;->onCreate(Landroid/os/Bundle;)V",
            "source_dex_path": "/data/data/com.termux/files/home/AndroidProjects/shadcn/app/build/outputs/apk/debug/app-debug.apk"
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
  --input-json '{"input":["/data/data/com.termux/files/home/AndroidProjects/shadcn/app/build/outputs/apk/debug/app-debug.apk"],"method_anchor":{"class_name":"com.shadcn.ui.compose.MainActivity","method_name":"onCreate","descriptor":"(Landroid/os/Bundle;)V"},"limit":5}'
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
  --input-json '{"input":["/path/to/main-activity.dex"],"method_anchor":{"class_name":"com.shadcn.ui.compose.MainActivity","method_name":"onCreate"}}'
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
          "path": "/tmp/dexclub-analyst-runs/<run-id>/exports/com_shadcn_ui_compose_MainActivity.smali",
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
        "export_path": "/tmp/dexclub-analyst-runs/<run-id>/exports/com_shadcn_ui_compose_MainActivity.smali"
      }
    }
  ],
  "summary": {
    "text": "Summarized one exported method body.",
    "style": "partial_support"
  }
}
```

## `summarize_method_logic` with overloaded target

Command:

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py run \
  --task-type summarize_method_logic \
  --input-json '{"input":["/path/to/foundation-image.dex"],"method_anchor":{"class_name":"androidx.compose.foundation.ImageKt","method_name":"Image"}}'
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
        "export_path": "/tmp/dexclub-analyst-runs/<run-id>/exports/androidx_compose_foundation_ImageKt.smali"
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
  --input-json '{"input":["/path/to/foundation-image.dex"],"method_anchor":{"class_name":"androidx.compose.foundation.ImageKt","method_name":"Image","descriptor":"Landroidx/compose/foundation/ImageKt;->Image(Landroidx/compose/ui/graphics/ImageBitmap;Ljava/lang/String;Landroidx/compose/ui/Modifier;Landroidx/compose/ui/Alignment;Landroidx/compose/ui/layout/ContentScale;FLandroidx/compose/ui/graphics/ColorFilter;Landroidx/compose/runtime/Composer;II)V"}}'
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
        "export_path": "/tmp/dexclub-analyst-runs/<run-id>/exports/androidx_compose_foundation_ImageKt.smali"
      }
    }
  ],
  "summary": {
    "text": "Summarized one exact method body.",
    "style": "partial_support"
  }
}
```

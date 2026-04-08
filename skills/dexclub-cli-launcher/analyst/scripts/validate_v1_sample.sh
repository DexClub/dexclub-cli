#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANALYZE="$SCRIPT_DIR/analyze.py"
RUN_FIND="$SCRIPT_DIR/run_find.py"
EXPORT_AND_SCAN="$SCRIPT_DIR/export_and_scan.py"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"

SAMPLE_APK="${1:-${DEXCLUB_ANALYST_SAMPLE_APK:-}}"
TMP_BASE_DIR="${DEXCLUB_ANALYST_VALIDATE_TMP_BASE:-$REPO_ROOT/build/dexclub-cli/tmp}"

if [[ -z "$SAMPLE_APK" ]]; then
  cat >&2 <<'EOF'
usage: validate_v1_sample.sh <sample-apk-path>

Provide the sample APK path as the first argument, or set
DEXCLUB_ANALYST_SAMPLE_APK=<sample-apk-path>.
EOF
  exit 2
fi

mkdir -p "$TMP_BASE_DIR"
TMP_ROOT="${DEXCLUB_ANALYST_VALIDATE_TMP:-$(mktemp -d "$TMP_BASE_DIR/dexclub-analyst-v1.XXXXXX")}"
DEX_DIR="$TMP_ROOT/dex"
RESULT_DIR="$TMP_ROOT/results"

mkdir -p "$DEX_DIR" "$RESULT_DIR"

if [[ ! -f "$SAMPLE_APK" ]]; then
  echo "sample apk not found: $SAMPLE_APK" >&2
  exit 1
fi

assert_expr() {
  local file="$1"
  local expr="$2"
  local message="$3"
  python3 - "$file" "$expr" "$message" <<'PY'
import json
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
expr = sys.argv[2]
message = sys.argv[3]
text = path.read_text(encoding="utf-8")
try:
    payload = json.loads(text)
except json.JSONDecodeError:
    payload = None
    lines = text.splitlines()
    for start in range(len(lines)):
        candidate = "\n".join(lines[start:]).strip()
        if candidate[:1] not in "[{":
            continue
        try:
            payload = json.loads(candidate)
            break
        except json.JSONDecodeError:
            continue
    if payload is None:
        raise SystemExit(f"{path.name}: unable to locate JSON payload")
ok = eval(expr, {}, {"payload": payload, "Path": pathlib.Path})
if not ok:
    raise SystemExit(f"{path.name}: {message}")
PY
}

stdout_has_hits() {
  local file="$1"
  python3 - "$file" <<'PY'
import json
import pathlib
import sys

text = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
lines = text.splitlines()
payload = None
for start in range(len(lines)):
    candidate = "\n".join(lines[start:]).strip()
    if candidate[:1] not in "[{":
        continue
    try:
        payload = json.loads(candidate)
        break
    except json.JSONDecodeError:
        continue
if payload:
    raise SystemExit(0)
raise SystemExit(1)
PY
}

run_case() {
  local name="$1"
  local task_type="$2"
  local input_json="$3"
  local output_path="$RESULT_DIR/$name.json"
  python3 "$ANALYZE" run --task-type "$task_type" --input-json "$input_json" >"$output_path"
  echo "validated_output=$output_path"
}

locate_class_dex() {
  local class_name="$1"
  local method_name="$2"
  local located_dex=""
  for dex_path in "$DEX_DIR"/classes*.dex; do
    local locate_output="$RESULT_DIR/locate-$(basename "$dex_path")-$(echo "$class_name" | tr './$' '___').txt"
    python3 "$RUN_FIND" \
      --input "$dex_path" \
      --output-format json \
      --limit 10 \
      method \
      --declared-class "$class_name" \
      --method-name "$method_name" >"$locate_output"
    if stdout_has_hits "$locate_output"; then
      located_dex="$dex_path"
      break
    fi
  done
  if [[ -z "$located_dex" ]]; then
    echo "failed to locate dex containing $class_name#$method_name" >&2
    exit 1
  fi
  printf '%s\n' "$located_dex"
}

echo "sample_apk=$SAMPLE_APK"
echo "tmp_root=$TMP_ROOT"

while IFS= read -r dex_name; do
  unzip -p "$SAMPLE_APK" "$dex_name" >"$DEX_DIR/$dex_name"
done < <(unzip -Z1 "$SAMPLE_APK" 'classes*.dex' | sort)

main_activity_dex="$(locate_class_dex "com.shadcn.ui.compose.MainActivity" "onCreate")"
ambiguous_image_dex="$(locate_class_dex "androidx.compose.foundation.ImageKt" "Image")"
echo "main_activity_dex=$main_activity_dex"
echo "ambiguous_image_dex=$ambiguous_image_dex"

string_input=$(cat <<JSON
{"input":["$SAMPLE_APK"],"string":"https://github.com/shadcn.png","declared_class":"com.shadcn.ui.compose.showcase.docs.AvatarDocsPageKt"}
JSON
)
run_case "search_methods_by_string" "search_methods_by_string" "$string_input"
assert_expr "$RESULT_DIR/search_methods_by_string.json" "payload['status'] == 'ok'" "search string should succeed"
assert_expr "$RESULT_DIR/search_methods_by_string.json" "payload['step_results'][0]['result']['count'] == 1" "search string should return exactly one hit"
assert_expr "$RESULT_DIR/search_methods_by_string.json" "payload['step_results'][0]['result']['items'][0]['class_name'] == 'com.shadcn.ui.compose.showcase.docs.AvatarDocsPageKt'" "search string should hit AvatarDocsPageKt"

number_input=$(cat <<JSON
{"input":["$main_activity_dex"],"number":"0x3","declared_class":"com.shadcn.ui.compose.MainActivity"}
JSON
)
run_case "search_methods_by_number" "search_methods_by_number" "$number_input"
assert_expr "$RESULT_DIR/search_methods_by_number.json" "payload['status'] == 'ok'" "search number should succeed"
assert_expr "$RESULT_DIR/search_methods_by_number.json" "payload['step_results'][0]['result']['items'][0]['method_name'] == 'onCreate'" "search number should hit MainActivity.onCreate"

trace_callers_input=$(cat <<JSON
{"input":["$SAMPLE_APK"],"method_anchor":{"class_name":"androidx.activity.ComponentActivity","method_name":"onCreate"},"limit":3}
JSON
)
run_case "trace_callers" "trace_callers" "$trace_callers_input"
assert_expr "$RESULT_DIR/trace_callers.json" "payload['status'] == 'ok'" "trace callers should succeed"
assert_expr "$RESULT_DIR/trace_callers.json" "payload['step_results'][0]['result']['relation_direction'] == 'callers'" "trace callers should keep relation direction"
assert_expr "$RESULT_DIR/trace_callers.json" "any(item['class_name'] == 'com.shadcn.ui.compose.MainActivity' for item in payload['step_results'][0]['result']['items'])" "trace callers should include MainActivity.onCreate"

trace_callees_input=$(cat <<JSON
{"input":["$SAMPLE_APK"],"method_anchor":{"class_name":"com.shadcn.ui.compose.MainActivity","method_name":"onCreate"},"limit":5}
JSON
)
run_case "trace_callees" "trace_callees" "$trace_callees_input"
assert_expr "$RESULT_DIR/trace_callees.json" "payload['status'] == 'ok'" "trace callees should succeed"
assert_expr "$RESULT_DIR/trace_callees.json" "payload['step_results'][0]['result']['relation_direction'] == 'callees'" "trace callees should keep relation direction"
assert_expr "$RESULT_DIR/trace_callees.json" 'any(item["method_name"] == "setContent$default" for item in payload["step_results"][0]["result"]["items"])' "trace callees should include setContent\$default"

trace_callees_descriptor_input=$(cat <<JSON
{"input":["$SAMPLE_APK"],"method_anchor":{"class_name":"com.shadcn.ui.compose.MainActivity","method_name":"onCreate","descriptor":"(Landroid/os/Bundle;)V"},"limit":5}
JSON
)
run_case "trace_callees_descriptor" "trace_callees" "$trace_callees_descriptor_input"
assert_expr "$RESULT_DIR/trace_callees_descriptor.json" "payload['status'] == 'ok'" "descriptor-aware trace callees should succeed"
assert_expr "$RESULT_DIR/trace_callees_descriptor.json" "payload['step_results'][0]['result']['anchor']['descriptor'] == 'Lcom/shadcn/ui/compose/MainActivity;->onCreate(Landroid/os/Bundle;)V'" "descriptor-aware trace should normalize the full descriptor"
assert_expr "$RESULT_DIR/trace_callees_descriptor.json" 'any(item["method_name"] == "setContent$default" for item in payload["step_results"][0]["result"]["items"])' "descriptor-aware trace should preserve exact callee hits"

summarize_input=$(cat <<JSON
{"input":["$main_activity_dex"],"method_anchor":{"class_name":"com.shadcn.ui.compose.MainActivity","method_name":"onCreate"}}
JSON
)
run_case "summarize_method_logic" "summarize_method_logic" "$summarize_input"
assert_expr "$RESULT_DIR/summarize_method_logic.json" "payload['status'] == 'ok'" "summarize method logic should succeed"
assert_expr "$RESULT_DIR/summarize_method_logic.json" "payload['step_results'][0]['result']['kind'] == 'smali'" "summarize method logic should export smali by default"
assert_expr "$RESULT_DIR/summarize_method_logic.json" "payload['step_results'][0]['result']['method_call_count'] == 4" "summarize method logic should report four direct calls in sample"
assert_expr "$RESULT_DIR/summarize_method_logic.json" "payload['step_results'][0]['result']['structured_summary']['supported'] is True" "smali summarize should expose a structured block outline"
assert_expr "$RESULT_DIR/summarize_method_logic.json" "payload['step_results'][0]['result']['structured_summary']['basic_block_count'] >= 1" "structured block outline should contain at least one basic block"
assert_expr "$RESULT_DIR/summarize_method_logic.json" "payload['step_results'][0]['result']['structured_summary']['call_cluster_count'] >= 1" "small smali summarize should still expose call clusters"
assert_expr "$RESULT_DIR/summarize_method_logic.json" "payload['step_results'][0]['result']['structured_summary']['focus_snippet_count'] >= 1" "smali summarize should expose focused snippets"
assert_expr "$RESULT_DIR/summarize_method_logic.json" 'any("setContent$default" in snippet["code"] for snippet in payload["step_results"][0]["result"]["structured_summary"]["focus_snippets"])' "focused snippets should keep key invoke context"
assert_expr "$RESULT_DIR/summarize_method_logic.json" "payload['step_results'][0]['result']['large_method_analysis']['is_large_method'] is False" "small summarize sample should not be marked as a large method"
assert_expr "$RESULT_DIR/summarize_method_logic.json" "Path(payload['step_results'][0]['result']['export_path']).is_file()" "summarize method logic should keep exported artifact"

summarize_apk_input=$(cat <<JSON
{"input":["$SAMPLE_APK"],"method_anchor":{"class_name":"com.shadcn.ui.compose.MainActivity","method_name":"onCreate"}}
JSON
)
run_case "summarize_method_logic_apk" "summarize_method_logic" "$summarize_apk_input"
assert_expr "$RESULT_DIR/summarize_method_logic_apk.json" "payload['status'] == 'ok'" "apk summarize should succeed"
assert_expr "$RESULT_DIR/summarize_method_logic_apk.json" "len(payload['plan']['steps']) == 2" "apk summarize should plan a resolve step plus export step"
assert_expr "$RESULT_DIR/summarize_method_logic_apk.json" "payload['step_results'][0]['step_kind'] == 'resolve_apk_dex'" "apk summarize should resolve a target dex first"
assert_expr "$RESULT_DIR/summarize_method_logic_apk.json" "payload['step_results'][1]['step_kind'] == 'export_and_scan'" "apk summarize should export after dex resolution"
assert_expr "$RESULT_DIR/summarize_method_logic_apk.json" "payload['step_results'][0]['result']['resolved_dex_path'].endswith('classes4.dex')" "apk summarize should resolve MainActivity to classes4.dex in sample"
assert_expr "$RESULT_DIR/summarize_method_logic_apk.json" "payload['step_results'][1]['result']['method_call_count'] == 4" "apk summarize should preserve exported method analysis"

overflow_input=$(cat <<JSON
{"input":["$SAMPLE_APK"],"string":"description"}
JSON
)
run_case "threshold_overflow" "search_methods_by_string" "$overflow_input"
assert_expr "$RESULT_DIR/threshold_overflow.json" "payload['status'] == 'ok'" "threshold overflow should still return ok with preview"
assert_expr "$RESULT_DIR/threshold_overflow.json" "payload['step_results'][0]['result']['truncated'] is True" "threshold overflow should mark preview truncated"
assert_expr "$RESULT_DIR/threshold_overflow.json" "payload['recommendations'][0]['reason'] == 'max_direct_hits_exceeded'" "threshold overflow should emit narrowing recommendation"

unsupported_input=$(cat <<JSON
{"input":["$(python3 - "$RESULT_DIR/summarize_method_logic.json" <<'PY'
import json
import pathlib
import sys

payload = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
print(payload["step_results"][0]["result"]["export_path"])
PY
)"],"method_anchor":{"class_name":"com.shadcn.ui.compose.MainActivity","method_name":"onCreate"}}
JSON
)
run_case "unsupported_exported_code_summarize" "summarize_method_logic" "$unsupported_input"
assert_expr "$RESULT_DIR/unsupported_exported_code_summarize.json" "payload['status'] == 'unsupported'" "exported code summarize input should be rejected"

ambiguous_input=$(cat <<JSON
{"input":["$ambiguous_image_dex"],"method_anchor":{"class_name":"androidx.compose.foundation.ImageKt","method_name":"Image"}}
JSON
)
run_case "ambiguous_summarize_method_logic" "summarize_method_logic" "$ambiguous_input"
assert_expr "$RESULT_DIR/ambiguous_summarize_method_logic.json" "payload['status'] == 'ambiguous'" "overloaded summarize target should return ambiguous"
assert_expr "$RESULT_DIR/ambiguous_summarize_method_logic.json" "payload['recommendations'][0]['reason'] == 'overload_ambiguity'" "ambiguous summarize should emit overload guidance"

exact_descriptor="Landroidx/compose/foundation/ImageKt;->Image(Landroidx/compose/ui/graphics/ImageBitmap;Ljava/lang/String;Landroidx/compose/ui/Modifier;Landroidx/compose/ui/Alignment;Landroidx/compose/ui/layout/ContentScale;FLandroidx/compose/ui/graphics/ColorFilter;Landroidx/compose/runtime/Composer;II)V"
exact_summarize_input=$(cat <<JSON
{"input":["$ambiguous_image_dex"],"method_anchor":{"class_name":"androidx.compose.foundation.ImageKt","method_name":"Image","descriptor":"$exact_descriptor"}}
JSON
)
run_case "exact_summarize_method_logic" "summarize_method_logic" "$exact_summarize_input"
assert_expr "$RESULT_DIR/exact_summarize_method_logic.json" "payload['status'] == 'ok'" "descriptor-aware summarize should disambiguate overloaded methods"
assert_expr "$RESULT_DIR/exact_summarize_method_logic.json" "payload['step_results'][0]['result']['scope']['method_descriptor'] == '$exact_descriptor'" "descriptor-aware summarize should keep the exact scoped descriptor"
assert_expr "$RESULT_DIR/exact_summarize_method_logic.json" "payload['step_results'][0]['result']['structured_summary']['basic_block_count'] >= 10" "large smali summarize should expose multiple basic blocks"
assert_expr "$RESULT_DIR/exact_summarize_method_logic.json" "payload['step_results'][0]['result']['structured_summary']['call_cluster_count'] >= 1" "large smali summarize should expose call clusters"
assert_expr "$RESULT_DIR/exact_summarize_method_logic.json" "payload['step_results'][0]['result']['structured_summary']['constant_cluster_count'] >= 1" "large smali summarize should expose constant clusters"
assert_expr "$RESULT_DIR/exact_summarize_method_logic.json" "payload['step_results'][0]['result']['structured_summary']['focus_snippet_count'] >= 3" "large smali summarize should expose multiple focused snippets"
assert_expr "$RESULT_DIR/exact_summarize_method_logic.json" "any(snippet['source_kind'] == 'call_cluster' for snippet in payload['step_results'][0]['result']['structured_summary']['focus_snippets'])" "focused snippets should cover call clusters"
assert_expr "$RESULT_DIR/exact_summarize_method_logic.json" "any(snippet['truncated'] is True for snippet in payload['step_results'][0]['result']['structured_summary']['focus_snippets'])" "large-method focused snippets should truncate oversized ranges"
assert_expr "$RESULT_DIR/exact_summarize_method_logic.json" "any('branches' in block['summary_tags'] for block in payload['step_results'][0]['result']['structured_summary']['basic_blocks'])" "structured block outline should tag branch-heavy blocks"
assert_expr "$RESULT_DIR/exact_summarize_method_logic.json" "payload['step_results'][0]['result']['large_method_analysis']['is_large_method'] is True" "large smali summarize should surface grouped compression"
assert_expr "$RESULT_DIR/exact_summarize_method_logic.json" "payload['step_results'][0]['result']['large_method_analysis']['line_threshold'] == 120" "large method threshold should stay stable in v1"
assert_expr "$RESULT_DIR/exact_summarize_method_logic.json" "any(group['kind'] == 'branch_hotspots' for group in payload['step_results'][0]['result']['large_method_analysis']['groups'])" "large method compression should include branch hotspots"
assert_expr "$RESULT_DIR/exact_summarize_method_logic.json" "any(group['kind'] == 'method_calls' and len(group['clusters']) >= 1 for group in payload['step_results'][0]['result']['large_method_analysis']['groups'])" "large method compression should cluster direct call hotspots"

exact_java_export_output="$RESULT_DIR/exact_java_export_and_scan.json"
python3 "$EXPORT_AND_SCAN" \
  --input-dex "$ambiguous_image_dex" \
  --class "androidx.compose.foundation.ImageKt" \
  --method "Image" \
  --method-descriptor "$exact_descriptor" \
  --language java \
  --mode summary \
  --format json >"$exact_java_export_output"
echo "validated_output=$exact_java_export_output"
assert_expr "$exact_java_export_output" "payload['kind'] == 'java'" "direct export_and_scan should produce java output on the published release"
assert_expr "$exact_java_export_output" "payload['scope']['methodDescriptor'] == '$exact_descriptor'" "direct export_and_scan should keep the exact java-scoped descriptor"
assert_expr "$exact_java_export_output" "payload['methodCallCount'] >= 5" "direct export_and_scan should report java-side method calls"
assert_expr "$exact_java_export_output" "payload['branchLineCount'] >= 1" "direct export_and_scan should report java-side branch lines"
assert_expr "$exact_java_export_output" "payload['structuredSummary']['supported'] is False" "java summarize should still skip smali-only structured summary"
assert_expr "$exact_java_export_output" "Path(payload['exportPath']).is_file()" "direct export_and_scan should keep the exported java artifact"

exact_java_input=$(cat <<JSON
{"input":["$SAMPLE_APK"],"method_anchor":{"class_name":"androidx.compose.foundation.ImageKt","method_name":"Image","descriptor":"$exact_descriptor"},"language":"java"}
JSON
)
run_case "exact_java_summarize_method_logic" "summarize_method_logic" "$exact_java_input"
assert_expr "$RESULT_DIR/exact_java_summarize_method_logic.json" "payload['status'] == 'ok'" "descriptor-aware java summarize should succeed on the published release"
assert_expr "$RESULT_DIR/exact_java_summarize_method_logic.json" "len(payload['plan']['steps']) == 2" "apk-backed java summarize should plan resolve plus export steps"
assert_expr "$RESULT_DIR/exact_java_summarize_method_logic.json" "payload['step_results'][0]['step_kind'] == 'resolve_apk_dex'" "apk-backed java summarize should resolve a target dex first"
assert_expr "$RESULT_DIR/exact_java_summarize_method_logic.json" "payload['step_results'][1]['step_kind'] == 'export_and_scan'" "apk-backed java summarize should export after dex resolution"
assert_expr "$RESULT_DIR/exact_java_summarize_method_logic.json" "payload['step_results'][1]['result']['kind'] == 'java'" "apk-backed java summarize should produce java output"
assert_expr "$RESULT_DIR/exact_java_summarize_method_logic.json" "payload['step_results'][1]['result']['scope']['method_descriptor'] == '$exact_descriptor'" "apk-backed java summarize should preserve the exact scoped descriptor"
assert_expr "$RESULT_DIR/exact_java_summarize_method_logic.json" "payload['step_results'][1]['result']['method_call_count'] >= 5" "apk-backed java summarize should preserve java call analysis"
assert_expr "$RESULT_DIR/exact_java_summarize_method_logic.json" "payload['step_results'][1]['result']['branch_line_count'] >= 1" "apk-backed java summarize should preserve java branch analysis"
assert_expr "$RESULT_DIR/exact_java_summarize_method_logic.json" "payload['step_results'][1]['result']['structured_summary']['supported'] is False" "java summarize should still report smali-only structured summary support"
assert_expr "$RESULT_DIR/exact_java_summarize_method_logic.json" "payload['step_results'][1]['result']['large_method_analysis']['is_large_method'] is False" "small java summarize should not be marked as a large method"
assert_expr "$RESULT_DIR/exact_java_summarize_method_logic.json" "Path(payload['step_results'][1]['result']['export_path']).is_file()" "apk-backed java summarize should keep the exported java artifact"

echo "validation=passed"
echo "results_dir=$RESULT_DIR"

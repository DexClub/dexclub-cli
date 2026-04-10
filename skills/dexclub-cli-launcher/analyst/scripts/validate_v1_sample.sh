#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANALYZE="$SCRIPT_DIR/analyze.py"
RUN_FIND="$SCRIPT_DIR/run_find.py"
EXPORT_AND_SCAN="$SCRIPT_DIR/export_and_scan.py"
RESOLVE_APK_DEX="$SCRIPT_DIR/resolve_apk_dex.py"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"

SAMPLE_APK="${1:-${DEXCLUB_ANALYST_SAMPLE_APK:-}}"
TMP_BASE_DIR="${DEXCLUB_ANALYST_VALIDATE_TMP_BASE:-$REPO_ROOT/.dexclub-cli/tmp}"

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
except json.JSONDecodeError as exc:
    raise SystemExit(f"{path.name}: stdout is not pure JSON: {exc.msg}") from exc
ok = eval(expr, {}, {"payload": payload, "Path": pathlib.Path})
if not ok:
    raise SystemExit(f"{path.name}: {message}")
PY
}

assert_file_contains() {
  local file="$1"
  local needle="$2"
  local message="$3"
  if ! grep -Fq "$needle" "$file"; then
    echo "$(basename "$file"): $message" >&2
    exit 1
  fi
}

stdout_has_hits() {
  local file="$1"
  python3 - "$file" <<'PY'
import json
import pathlib
import sys

text = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
payload = json.loads(text)
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

dex_list_json="$(python3 - "$DEX_DIR"/classes*.dex <<'PY'
import json
import pathlib
import sys

paths = [str(pathlib.Path(path).resolve()) for path in sys.argv[1:]]
print(json.dumps(paths, ensure_ascii=False))
PY
)"
reversed_dex_list_json="$(python3 - "$DEX_DIR"/classes*.dex <<'PY'
import json
import pathlib
import sys

paths = [str(pathlib.Path(path).resolve()) for path in sys.argv[1:]]
paths.reverse()
print(json.dumps(paths, ensure_ascii=False))
PY
)"

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
assert_expr "$RESULT_DIR/search_methods_by_string.json" "payload['input_source'] == 'apk_direct'" "apk-backed string search should report apk_direct input source"

string_dex_dir_input=$(cat <<JSON
{"input":{"dex_dir":"$DEX_DIR"},"string":"https://github.com/shadcn.png","declared_class":"com.shadcn.ui.compose.showcase.docs.AvatarDocsPageKt"}
JSON
)
run_case "search_methods_by_string_dex_dir" "search_methods_by_string" "$string_dex_dir_input"
assert_expr "$RESULT_DIR/search_methods_by_string_dex_dir.json" "payload['status'] == 'ok'" "dex-dir string search should succeed"
assert_expr "$RESULT_DIR/search_methods_by_string_dex_dir.json" "payload['input_source'] == 'workspace_dex_set'" "dex-dir string search should report workspace_dex_set input source"
assert_expr "$RESULT_DIR/search_methods_by_string_dex_dir.json" "payload['step_results'][0]['result']['items'][0]['class_name'] == 'com.shadcn.ui.compose.showcase.docs.AvatarDocsPageKt'" "dex-dir string search should preserve the matching class"

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

trace_callees_dex_list_input=$(cat <<JSON
{"input":{"dex_list":$reversed_dex_list_json},"method_anchor":{"class_name":"com.shadcn.ui.compose.MainActivity","method_name":"onCreate"},"limit":5}
JSON
)
run_case "trace_callees_dex_list" "trace_callees" "$trace_callees_dex_list_input"
assert_expr "$RESULT_DIR/trace_callees_dex_list.json" "payload['status'] == 'ok'" "dex-list trace callees should succeed"
assert_expr "$RESULT_DIR/trace_callees_dex_list.json" "payload['input_source'] == 'workspace_dex_set'" "dex-list trace callees should report workspace_dex_set input source"
assert_expr "$RESULT_DIR/trace_callees_dex_list.json" 'any(item["method_name"] == "setContent$default" for item in payload["step_results"][0]["result"]["items"])' "dex-list trace callees should preserve exact hits"

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
assert_expr "$RESULT_DIR/summarize_method_logic.json" "payload['input_source'] == 'workspace_dex_set'" "direct dex summarize should report workspace_dex_set input source"
assert_expr "$RESULT_DIR/summarize_method_logic.json" "payload['step_results'][0]['result']['kind'] == 'smali'" "summarize method logic should export smali by default"
assert_expr "$RESULT_DIR/summarize_method_logic.json" "payload['step_results'][0]['result']['method_call_count'] == 4" "summarize method logic should report four direct calls in sample"
assert_expr "$RESULT_DIR/summarize_method_logic.json" "payload['step_results'][0]['result']['structured_summary']['supported'] is True" "smali summarize should expose a structured block outline"
assert_expr "$RESULT_DIR/summarize_method_logic.json" "payload['step_results'][0]['result']['structured_summary']['basic_block_count'] >= 1" "structured block outline should contain at least one basic block"
assert_expr "$RESULT_DIR/summarize_method_logic.json" "payload['step_results'][0]['result']['structured_summary']['call_cluster_count'] >= 1" "small smali summarize should still expose call clusters"
assert_expr "$RESULT_DIR/summarize_method_logic.json" "payload['step_results'][0]['result']['structured_summary']['focus_snippet_count'] >= 1" "smali summarize should expose focused snippets"
assert_expr "$RESULT_DIR/summarize_method_logic.json" 'any("setContent$default" in snippet["code"] for snippet in payload["step_results"][0]["result"]["structured_summary"]["focus_snippets"])' "focused snippets should keep key invoke context"
assert_expr "$RESULT_DIR/summarize_method_logic.json" "payload['step_results'][0]['result']['large_method_analysis']['is_large_method'] is False" "small summarize sample should not be marked as a large method"
assert_expr "$RESULT_DIR/summarize_method_logic.json" "Path(payload['step_results'][0]['result']['export_path']).is_file()" "summarize method logic should keep exported artifact"
assert_expr "$RESULT_DIR/summarize_method_logic.json" "isinstance(payload['verifiedFacts'], list) and len(payload['verifiedFacts']) >= 2" "summarize method logic should expose verified facts"
assert_expr "$RESULT_DIR/summarize_method_logic.json" "isinstance(payload['inferences'], list) and len(payload['inferences']) >= 1" "summarize method logic should expose at least one inference"
assert_expr "$RESULT_DIR/summarize_method_logic.json" "isinstance(payload['unknowns'], list)" "summarize method logic should expose unknowns"
assert_expr "$RESULT_DIR/summarize_method_logic.json" "isinstance(payload['nextChecks'], list) and any(item['reason'] == 'focus_snippets_available' for item in payload['nextChecks'])" "summarize method logic should expose focused follow-up checks"
assert_expr "$RESULT_DIR/summarize_method_logic.json" "any(item.get('evidence') for item in payload['verifiedFacts'])" "verified facts should carry evidence locators when available"

summarize_apk_input=$(cat <<JSON
{"input":["$SAMPLE_APK"],"method_anchor":{"class_name":"com.shadcn.ui.compose.MainActivity","method_name":"onCreate"}}
JSON
)
run_case "summarize_method_logic_apk" "summarize_method_logic" "$summarize_apk_input"
assert_expr "$RESULT_DIR/summarize_method_logic_apk.json" "payload['status'] == 'ok'" "apk summarize should succeed"
assert_expr "$RESULT_DIR/summarize_method_logic_apk.json" "payload['input_source'] == 'apk_cached_extracted_dex'" "apk summarize should report apk_cached_extracted_dex input source"
assert_expr "$RESULT_DIR/summarize_method_logic_apk.json" "len(payload['plan']['steps']) == 2" "apk summarize should plan a resolve step plus export step"
assert_expr "$RESULT_DIR/summarize_method_logic_apk.json" "payload['step_results'][0]['step_kind'] == 'resolve_apk_dex'" "apk summarize should resolve a target dex first"
assert_expr "$RESULT_DIR/summarize_method_logic_apk.json" "payload['step_results'][1]['step_kind'] == 'export_and_scan'" "apk summarize should export after dex resolution"
assert_expr "$RESULT_DIR/summarize_method_logic_apk.json" "payload['step_results'][0]['result']['resolved_dex_path'].endswith('classes4.dex')" "apk summarize should resolve MainActivity to classes4.dex in sample"
assert_expr "$RESULT_DIR/summarize_method_logic_apk.json" "payload['step_results'][1]['result']['method_call_count'] == 4" "apk summarize should preserve exported method analysis"

summarize_dex_dir_input=$(cat <<JSON
{"input":{"dex_dir":"$DEX_DIR"},"method_anchor":{"class_name":"com.shadcn.ui.compose.MainActivity","method_name":"onCreate"}}
JSON
)
run_case "summarize_method_logic_dex_dir" "summarize_method_logic" "$summarize_dex_dir_input"
assert_expr "$RESULT_DIR/summarize_method_logic_dex_dir.json" "payload['status'] == 'ok'" "dex-dir summarize should succeed"
assert_expr "$RESULT_DIR/summarize_method_logic_dex_dir.json" "payload['input_source'] == 'workspace_dex_set'" "dex-dir summarize should report workspace_dex_set input source"
assert_expr "$RESULT_DIR/summarize_method_logic_dex_dir.json" "len(payload['plan']['steps']) == 2" "dex-dir summarize should plan a resolve step plus export step"
assert_expr "$RESULT_DIR/summarize_method_logic_dex_dir.json" "payload['step_results'][0]['step_kind'] == 'resolve_workspace_dex_set'" "dex-dir summarize should resolve within the workspace dex set first"
assert_expr "$RESULT_DIR/summarize_method_logic_dex_dir.json" "payload['step_results'][1]['step_kind'] == 'export_and_scan'" "dex-dir summarize should export after workspace dex resolution"
assert_expr "$RESULT_DIR/summarize_method_logic_dex_dir.json" "payload['step_results'][0]['result']['resolved_dex_path'].endswith('classes4.dex')" "dex-dir summarize should resolve MainActivity to classes4.dex in sample"
assert_expr "$RESULT_DIR/summarize_method_logic_dex_dir.json" "payload['step_results'][1]['result']['method_call_count'] == 4" "dex-dir summarize should preserve exported method analysis"

cache_reuse_work_root="$TMP_ROOT/cache-reuse-work"
cache_reuse_cache_root="$TMP_ROOT/cache-reuse-cache"
cache_reuse_resolve_output="$RESULT_DIR/resolve_apk_dex_cache_reuse.json"
DEXCLUB_ANALYST_WORK_ROOT="$cache_reuse_work_root" \
DEXCLUB_ANALYST_CACHE_DIR="$cache_reuse_cache_root" \
python3 "$RESOLVE_APK_DEX" \
  --input "$SAMPLE_APK" \
  --class "com.shadcn.ui.compose.MainActivity" \
  --output-format json >"$cache_reuse_resolve_output"
echo "validated_output=$cache_reuse_resolve_output"
apk_main_activity_dex="$(python3 - "$cache_reuse_resolve_output" <<'PY'
import json
import pathlib
import sys

payload = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
print(payload["resolved_dex_path"])
PY
)"
apk_cache_reuse_output="$RESULT_DIR/export_and_scan_cache_from_apk.json"
DEXCLUB_ANALYST_WORK_ROOT="$cache_reuse_work_root" \
DEXCLUB_ANALYST_CACHE_DIR="$cache_reuse_cache_root" \
python3 "$EXPORT_AND_SCAN" \
  --input "$apk_main_activity_dex" \
  --class "com.shadcn.ui.compose.MainActivity" \
  --method "onCreate" \
  --language smali \
  --mode summary \
  --output-format json >"$apk_cache_reuse_output"
echo "validated_output=$apk_cache_reuse_output"
assert_expr "$apk_cache_reuse_output" "payload['cacheHit'] is False" "first apk-backed export_and_scan should populate the cache"

direct_cache_reuse_output="$RESULT_DIR/export_and_scan_cache_from_direct_dex.json"
DEXCLUB_ANALYST_WORK_ROOT="$cache_reuse_work_root" \
DEXCLUB_ANALYST_CACHE_DIR="$cache_reuse_cache_root" \
python3 "$EXPORT_AND_SCAN" \
  --input "$main_activity_dex" \
  --class "com.shadcn.ui.compose.MainActivity" \
  --method "onCreate" \
  --language smali \
  --mode summary \
  --output-format json >"$direct_cache_reuse_output"
echo "validated_output=$direct_cache_reuse_output"
assert_expr "$direct_cache_reuse_output" "payload['cacheHit'] is True" "direct dex export_and_scan should reuse the apk-backed cache entry"
apk_cache_path="$(python3 - "$apk_cache_reuse_output" <<'PY'
import json
import pathlib
import sys

payload = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
print(payload["cachePath"])
PY
)"
assert_expr "$direct_cache_reuse_output" "payload['cachePath'] == '$apk_cache_path'" "apk and direct dex export_and_scan should converge to the same cache path"
assert_expr "$direct_cache_reuse_output" "Path(payload['exportPath']).is_file()" "cache-backed direct dex export_and_scan should still materialize the exported artifact"

missing_apk_stderr="$RESULT_DIR/resolve_apk_dex_missing_input.stderr"
if python3 "$RESOLVE_APK_DEX" --input "$RESULT_DIR/does-not-exist.apk" --class "com.example.Missing" --output-format json >"$RESULT_DIR/resolve_apk_dex_missing_input.stdout" 2>"$missing_apk_stderr"; then
  echo "resolve_apk_dex missing-input case should fail" >&2
  exit 1
fi
assert_file_contains "$missing_apk_stderr" "Cause:" "resolve_apk_dex missing-input error should explain the cause"
assert_file_contains "$missing_apk_stderr" "Recommended action:" "resolve_apk_dex missing-input error should suggest the next action"

invalid_run_find_stderr="$RESULT_DIR/run_find_invalid_raw_query.stderr"
if python3 "$RUN_FIND" method --input "$main_activity_dex" --raw-query-json "{" --output-format json >"$RESULT_DIR/run_find_invalid_raw_query.stdout" 2>"$invalid_run_find_stderr"; then
  echo "run_find invalid raw-query-json case should fail" >&2
  exit 1
fi
assert_file_contains "$invalid_run_find_stderr" "Cause:" "run_find invalid-query error should explain the cause"
assert_file_contains "$invalid_run_find_stderr" "Recommended action:" "run_find invalid-query error should suggest the next action"

step_reuse_work_root="$TMP_ROOT/step-reuse-work"
step_reuse_cache_root="$TMP_ROOT/step-reuse-cache"
step_reuse_input=$(cat <<JSON
{"input":["$SAMPLE_APK"],"method_anchor":{"class_name":"com.shadcn.ui.compose.MainActivity","method_name":"onCreate"}}
JSON
)
step_reuse_first_output="$RESULT_DIR/step_reuse_first_run.json"
DEXCLUB_ANALYST_WORK_ROOT="$step_reuse_work_root" \
DEXCLUB_ANALYST_CACHE_DIR="$step_reuse_cache_root" \
python3 "$ANALYZE" run --task-type summarize_method_logic --input-json "$step_reuse_input" >"$step_reuse_first_output"
echo "validated_output=$step_reuse_first_output"
step_reuse_first_run_id="$(python3 - "$step_reuse_first_output" <<'PY'
import json
import pathlib
import sys

payload = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
print(payload["run_id"])
PY
)"
step_reuse_first_export_path="$(python3 - "$step_reuse_first_output" <<'PY'
import json
import pathlib
import sys

payload = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
print(payload["step_results"][1]["result"]["export_path"])
PY
)"
step_reuse_second_output="$RESULT_DIR/step_reuse_second_run.json"
DEXCLUB_ANALYST_WORK_ROOT="$step_reuse_work_root" \
DEXCLUB_ANALYST_CACHE_DIR="$step_reuse_cache_root" \
python3 "$ANALYZE" run --task-type summarize_method_logic --input-json "$step_reuse_input" >"$step_reuse_second_output"
echo "validated_output=$step_reuse_second_output"
step_reuse_second_run_id="$(python3 - "$step_reuse_second_output" <<'PY'
import json
import pathlib
import sys

payload = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
print(payload["run_id"])
PY
)"
assert_expr "$step_reuse_second_output" "payload['step_results'][0]['reused_from']['run_id'] == '$step_reuse_first_run_id'" "second summarize run should reuse the prior resolve_apk_dex step"
assert_expr "$step_reuse_second_output" "payload['step_results'][1]['reused_from']['run_id'] == '$step_reuse_first_run_id'" "second summarize run should reuse the prior export_and_scan step"
assert_expr "$step_reuse_second_output" "payload['step_results'][1]['result']['export_path'] != '$step_reuse_first_export_path'" "reused export_and_scan should materialize a fresh run-local export artifact"
assert_expr "$step_reuse_second_output" "Path(payload['step_results'][1]['result']['export_path']).is_file()" "reused export_and_scan should keep the rematerialized export artifact"
assert_expr "$step_reuse_second_output" "payload['reused_step_count'] == 2" "second summarize run should expose the reused step count"
assert_expr "$step_reuse_second_output" "payload['reused_step_kinds'] == ['resolve_apk_dex', 'export_and_scan']" "second summarize run should expose the reused step kinds in step order"
assert_expr "$step_reuse_second_output" "payload['cache_hit_count'] == 0" "fully reused summarize steps should not be double-counted as helper cache hits"
step_reuse_second_run_root="$(python3 - "$step_reuse_second_output" <<'PY'
import json
import pathlib
import sys

payload = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
print(payload["artifact_root"])
PY
)"
step_reuse_second_summary_path="$step_reuse_second_run_root/run-summary.json"
step_reuse_latest_path="$step_reuse_work_root/runs/v1/latest.json"
assert_expr "$step_reuse_second_summary_path" "payload['reused_step_count'] == 2" "run summary should expose the reused step count"
assert_expr "$step_reuse_second_summary_path" "payload['reused_step_kinds'] == ['resolve_apk_dex', 'export_and_scan']" "run summary should expose the reused step kinds"
assert_expr "$step_reuse_second_summary_path" "payload['cache_hit_count'] == 0" "run summary should keep helper cache-hit count separate from step reuse"
assert_expr "$step_reuse_latest_path" "payload['run_id'] == '$step_reuse_second_run_id'" "latest index should point at the latest summarize reuse run"
assert_expr "$step_reuse_latest_path" "payload['reused_step_count'] == 2" "latest index should expose the reused step count"
assert_expr "$step_reuse_latest_path" "payload['reused_step_kinds'] == ['resolve_apk_dex', 'export_and_scan']" "latest index should expose the reused step kinds"
assert_expr "$step_reuse_latest_path" "payload['cache_hit_count'] == 0" "latest index should keep helper cache-hit count separate from step reuse"

run_find_reuse_work_root="$TMP_ROOT/run-find-reuse-work"
run_find_reuse_cache_root="$TMP_ROOT/run-find-reuse-cache"
run_find_reuse_input=$(cat <<JSON
{"input":["$SAMPLE_APK"],"method_anchor":{"class_name":"com.shadcn.ui.compose.MainActivity","method_name":"onCreate"},"limit":5}
JSON
)
run_find_reuse_first_output="$RESULT_DIR/run_find_reuse_first_run.json"
DEXCLUB_ANALYST_WORK_ROOT="$run_find_reuse_work_root" \
DEXCLUB_ANALYST_CACHE_DIR="$run_find_reuse_cache_root" \
python3 "$ANALYZE" run --task-type trace_callees --input-json "$run_find_reuse_input" >"$run_find_reuse_first_output"
echo "validated_output=$run_find_reuse_first_output"
run_find_reuse_first_run_id="$(python3 - "$run_find_reuse_first_output" <<'PY'
import json
import pathlib
import sys

payload = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
print(payload["run_id"])
PY
)"
run_find_reuse_second_output="$RESULT_DIR/run_find_reuse_second_run.json"
DEXCLUB_ANALYST_WORK_ROOT="$run_find_reuse_work_root" \
DEXCLUB_ANALYST_CACHE_DIR="$run_find_reuse_cache_root" \
python3 "$ANALYZE" run --task-type trace_callees --input-json "$run_find_reuse_input" >"$run_find_reuse_second_output"
echo "validated_output=$run_find_reuse_second_output"
assert_expr "$run_find_reuse_second_output" "payload['step_results'][0]['reused_from']['run_id'] == '$run_find_reuse_first_run_id'" "second trace_callees run should reuse the prior run_find step"
assert_expr "$run_find_reuse_second_output" "payload['step_results'][0]['result']['relation_direction'] == 'callees'" "reused run_find step should preserve the normalized relation payload"
assert_expr "$run_find_reuse_second_output" 'any(item["method_name"] == "setContent$default" for item in payload["step_results"][0]["result"]["items"])' "reused run_find step should preserve the original hit set"
assert_expr "$run_find_reuse_second_output" "payload['reused_step_count'] == 1" "second trace_callees run should expose the reused step count"
assert_expr "$run_find_reuse_second_output" "payload['reused_step_kinds'] == ['run_find']" "second trace_callees run should expose the reused step kind"
assert_expr "$run_find_reuse_second_output" "payload['cache_hit_count'] == 0" "run_find reuse should not fabricate helper cache hits"

release_tag_reuse_work_root="$TMP_ROOT/release-tag-reuse-work"
release_tag_reuse_cache_root="$TMP_ROOT/release-tag-reuse-cache"
release_tag_reuse_input=$(cat <<JSON
{"input":["$SAMPLE_APK"],"method_anchor":{"class_name":"com.shadcn.ui.compose.MainActivity","method_name":"onCreate"},"limit":5}
JSON
)
release_tag_first_output="$RESULT_DIR/release_tag_reuse_first_run.json"
DEXCLUB_ANALYST_WORK_ROOT="$release_tag_reuse_work_root" \
DEXCLUB_ANALYST_CACHE_DIR="$release_tag_reuse_cache_root" \
DEXCLUB_ANALYST_RELEASE_TAG_OVERRIDE="release-a" \
python3 "$ANALYZE" run --task-type trace_callees --input-json "$release_tag_reuse_input" >"$release_tag_first_output"
echo "validated_output=$release_tag_first_output"
release_tag_second_output="$RESULT_DIR/release_tag_reuse_second_run.json"
DEXCLUB_ANALYST_WORK_ROOT="$release_tag_reuse_work_root" \
DEXCLUB_ANALYST_CACHE_DIR="$release_tag_reuse_cache_root" \
DEXCLUB_ANALYST_RELEASE_TAG_OVERRIDE="release-b" \
python3 "$ANALYZE" run --task-type trace_callees --input-json "$release_tag_reuse_input" >"$release_tag_second_output"
echo "validated_output=$release_tag_second_output"
assert_expr "$release_tag_second_output" "all('reused_from' not in step for step in payload['step_results'])" "different release tags should isolate step reuse"
release_tag_second_run_id="$(python3 - "$release_tag_second_output" <<'PY'
import json
import pathlib
import sys

payload = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
print(payload["run_id"])
PY
)"
release_tag_index_path="$release_tag_reuse_work_root/runs/v1/reusable-step-index-v1.json"
python3 - "$release_tag_index_path" <<'PY'
import json
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
payload = json.loads(path.read_text(encoding="utf-8"))
payload.setdefault("entries", {})["bogus-entry"] = {
    "run_id": "stale-run",
    "run_root": "/nonexistent/run",
    "step_id": "step-9",
    "step_kind": "run_find",
    "step_result_path": "/nonexistent/step-result.json",
    "release_tag": "release-b",
    "updated_at": "1970-01-01T00:00:00+00:00",
}
path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY
release_tag_third_output="$RESULT_DIR/release_tag_reuse_third_run.json"
DEXCLUB_ANALYST_WORK_ROOT="$release_tag_reuse_work_root" \
DEXCLUB_ANALYST_CACHE_DIR="$release_tag_reuse_cache_root" \
DEXCLUB_ANALYST_RELEASE_TAG_OVERRIDE="release-b" \
python3 "$ANALYZE" run --task-type trace_callees --input-json "$release_tag_reuse_input" >"$release_tag_third_output"
echo "validated_output=$release_tag_third_output"
assert_expr "$release_tag_third_output" "payload['step_results'][0]['reused_from']['run_id'] == '$release_tag_second_run_id'" "matching release tags should still allow run_find reuse"
release_tag_index_has_bogus="$(python3 - "$release_tag_index_path" <<'PY'
import json
import pathlib
import sys

payload = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
print("true" if "bogus-entry" in payload.get("entries", {}) else "false")
PY
)"
assert_expr "$release_tag_third_output" "'$release_tag_index_has_bogus' == 'false'" "invalid reusable-step-index entries should be pruned"

cache_hit_run_work_root="$TMP_ROOT/cache-hit-run-work"
cache_hit_run_cache_root="$TMP_ROOT/cache-hit-run-cache"
cache_hit_run_input=$(cat <<JSON
{"input":["$SAMPLE_APK"],"method_anchor":{"class_name":"com.shadcn.ui.compose.MainActivity","method_name":"onCreate"}}
JSON
)
cache_hit_run_first_output="$RESULT_DIR/cache_hit_run_first.json"
DEXCLUB_ANALYST_WORK_ROOT="$cache_hit_run_work_root" \
DEXCLUB_ANALYST_CACHE_DIR="$cache_hit_run_cache_root" \
DEXCLUB_ANALYST_RELEASE_TAG_OVERRIDE="cache-hit-a" \
python3 "$ANALYZE" run --task-type summarize_method_logic --input-json "$cache_hit_run_input" >"$cache_hit_run_first_output"
echo "validated_output=$cache_hit_run_first_output"
assert_expr "$cache_hit_run_first_output" "payload['cache_hit_count'] == 0" "first summarize run should start with zero helper cache hits"
cache_hit_run_second_output="$RESULT_DIR/cache_hit_run_second.json"
DEXCLUB_ANALYST_WORK_ROOT="$cache_hit_run_work_root" \
DEXCLUB_ANALYST_CACHE_DIR="$cache_hit_run_cache_root" \
DEXCLUB_ANALYST_RELEASE_TAG_OVERRIDE="cache-hit-b" \
python3 "$ANALYZE" run --task-type summarize_method_logic --input-json "$cache_hit_run_input" >"$cache_hit_run_second_output"
echo "validated_output=$cache_hit_run_second_output"
assert_expr "$cache_hit_run_second_output" "payload['reused_step_count'] == 0" "different release tags should still prevent summarize step reuse while testing helper cache hits"
assert_expr "$cache_hit_run_second_output" "payload['reused_step_kinds'] == []" "different release tags should keep the summarize reuse-kind summary empty"
assert_expr "$cache_hit_run_second_output" "payload['cache_hit_count'] >= 1" "summarize runs without step reuse should still expose helper cache hits"
cache_hit_run_second_run_id="$(python3 - "$cache_hit_run_second_output" <<'PY'
import json
import pathlib
import sys

payload = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
print(payload["run_id"])
PY
)"
cache_hit_run_second_run_root="$(python3 - "$cache_hit_run_second_output" <<'PY'
import json
import pathlib
import sys

payload = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
print(payload["artifact_root"])
PY
)"
cache_hit_run_second_summary_path="$cache_hit_run_second_run_root/run-summary.json"
cache_hit_latest_path="$cache_hit_run_work_root/runs/v1/latest.json"
assert_expr "$cache_hit_run_second_summary_path" "payload['reused_step_count'] == 0" "run summary should keep summarize helper-cache runs out of step reuse stats"
assert_expr "$cache_hit_run_second_summary_path" "payload['reused_step_kinds'] == []" "run summary should keep summarize helper-cache runs out of reuse kinds"
assert_expr "$cache_hit_run_second_summary_path" "payload['cache_hit_count'] >= 1" "run summary should expose helper cache hits"
assert_expr "$cache_hit_latest_path" "payload['run_id'] == '$cache_hit_run_second_run_id'" "latest index should point at the latest helper-cache summarize run"
assert_expr "$cache_hit_latest_path" "payload['reused_step_count'] == 0" "latest index should keep summarize helper-cache runs out of step reuse stats"
assert_expr "$cache_hit_latest_path" "payload['reused_step_kinds'] == []" "latest index should keep summarize helper-cache runs out of reuse kinds"
assert_expr "$cache_hit_latest_path" "payload['cache_hit_count'] >= 1" "latest index should expose helper cache hits"

cache_manage_work_root="$TMP_ROOT/cache-manage-work"
cache_manage_cache_root="$TMP_ROOT/cache-manage-cache"
cache_manage_input=$(cat <<JSON
{"input":["$SAMPLE_APK"],"method_anchor":{"class_name":"com.shadcn.ui.compose.MainActivity","method_name":"onCreate"}}
JSON
)
cache_manage_run_output="$RESULT_DIR/cache_manage_run.json"
DEXCLUB_ANALYST_WORK_ROOT="$cache_manage_work_root" \
DEXCLUB_ANALYST_CACHE_DIR="$cache_manage_cache_root" \
python3 "$ANALYZE" run --task-type summarize_method_logic --input-json "$cache_manage_input" >"$cache_manage_run_output"
echo "validated_output=$cache_manage_run_output"
cache_manage_run_id="$(python3 - "$cache_manage_run_output" <<'PY'
import json
import pathlib
import sys

payload = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
print(payload["run_id"])
PY
)"
cache_inspect_output="$RESULT_DIR/cache_inspect.json"
DEXCLUB_ANALYST_WORK_ROOT="$cache_manage_work_root" \
DEXCLUB_ANALYST_CACHE_DIR="$cache_manage_cache_root" \
python3 "$ANALYZE" cache inspect --format json >"$cache_inspect_output"
echo "validated_output=$cache_inspect_output"
assert_expr "$cache_inspect_output" "payload['inputs']['apk_entry_count'] >= 1" "cache inspect should report apk input cache entries"
assert_expr "$cache_inspect_output" "payload['export_and_scan']['entry_count'] >= 1" "cache inspect should report export-and-scan cache entries"
assert_expr "$cache_inspect_output" "payload['reusable_steps']['valid_entry_count'] >= 2" "cache inspect should report reusable summarize steps"
assert_expr "$cache_inspect_output" "payload['latest_run']['run_id'] == '$cache_manage_run_id'" "cache inspect should expose the latest run id"
assert_expr "$cache_inspect_output" "payload['latest_run']['task_type'] == 'summarize_method_logic'" "cache inspect should expose the latest run task type"
assert_expr "$cache_inspect_output" "payload['latest_run']['summary_exists'] is True" "cache inspect should expose the latest run summary linkage"
assert_expr "$cache_inspect_output" "payload['latest_run']['reused_step_count'] == 0" "cache inspect should expose latest-run reuse counters"
assert_expr "$cache_inspect_output" "payload['latest_run']['cache_hit_count'] == 0" "cache inspect should expose latest-run helper cache counters"
runs_latest_output="$RESULT_DIR/runs_latest.json"
DEXCLUB_ANALYST_WORK_ROOT="$cache_manage_work_root" \
DEXCLUB_ANALYST_CACHE_DIR="$cache_manage_cache_root" \
python3 "$ANALYZE" runs latest --format json >"$runs_latest_output"
echo "validated_output=$runs_latest_output"
assert_expr "$runs_latest_output" "payload['run']['run_id'] == '$cache_manage_run_id'" "runs latest should expose the latest run id"
assert_expr "$runs_latest_output" "payload['run']['task_type'] == 'summarize_method_logic'" "runs latest should expose the latest run task type"
assert_expr "$runs_latest_output" "payload['run']['summary_exists'] is True" "runs latest should resolve the summary payload"
assert_expr "$runs_latest_output" "payload['run']['final_result_exists'] is True" "runs latest should resolve the final result payload"
assert_expr "$runs_latest_output" "payload['run']['reused_step_count'] == 0" "runs latest should expose reuse counters"
assert_expr "$runs_latest_output" "payload['run']['cache_hit_count'] == 0" "runs latest should expose helper cache counters"
runs_inspect_output="$RESULT_DIR/runs_inspect.json"
DEXCLUB_ANALYST_WORK_ROOT="$cache_manage_work_root" \
DEXCLUB_ANALYST_CACHE_DIR="$cache_manage_cache_root" \
python3 "$ANALYZE" runs inspect --run-id "$cache_manage_run_id" --format json >"$runs_inspect_output"
echo "validated_output=$runs_inspect_output"
assert_expr "$runs_inspect_output" "payload['run']['run_id'] == '$cache_manage_run_id'" "runs inspect should expose the requested run id"
assert_expr "$runs_inspect_output" "payload['run']['summary_exists'] is True" "runs inspect should resolve the summary payload"
assert_expr "$runs_inspect_output" "payload['run']['final_result_exists'] is True" "runs inspect should resolve the final result payload"
assert_expr "$runs_inspect_output" "payload['run']['final_result_included'] is False" "runs inspect should keep final_result embedded output opt-in"
assert_expr "$runs_inspect_output" "payload['run']['summary_path'].endswith('/$cache_manage_run_id/run-summary.json')" "runs inspect should expose the run summary path"
assert_expr "$runs_inspect_output" "payload['run']['final_result_path'].endswith('/$cache_manage_run_id/final_result.json')" "runs inspect should expose the final result path"
runs_inspect_with_final_output="$RESULT_DIR/runs_inspect_with_final.json"
DEXCLUB_ANALYST_WORK_ROOT="$cache_manage_work_root" \
DEXCLUB_ANALYST_CACHE_DIR="$cache_manage_cache_root" \
python3 "$ANALYZE" runs inspect --run-id "$cache_manage_run_id" --include-final-result --format json >"$runs_inspect_with_final_output"
echo "validated_output=$runs_inspect_with_final_output"
assert_expr "$runs_inspect_with_final_output" "payload['run']['final_result_included'] is True" "runs inspect should embed final_result when requested"
assert_expr "$runs_inspect_with_final_output" "payload['run']['final_result']['run_id'] == '$cache_manage_run_id'" "embedded final_result should match the requested run id"
assert_expr "$runs_inspect_with_final_output" "payload['run']['final_result']['task_type'] == 'summarize_method_logic'" "embedded final_result should keep the run task type"
assert_expr "$runs_inspect_with_final_output" "payload['run']['final_result']['cache_hit_count'] == 0" "embedded final_result should preserve top-level reuse/cache counters"
assert_expr "$runs_inspect_with_final_output" "isinstance(payload['run']['final_result']['verifiedFacts'], list) and len(payload['run']['final_result']['verifiedFacts']) >= 1" "embedded final_result should keep verified facts"
assert_expr "$runs_inspect_with_final_output" "isinstance(payload['run']['final_result']['nextChecks'], list)" "embedded final_result should keep next checks"
runs_list_output="$RESULT_DIR/runs_list.json"
DEXCLUB_ANALYST_WORK_ROOT="$cache_manage_work_root" \
DEXCLUB_ANALYST_CACHE_DIR="$cache_manage_cache_root" \
python3 "$ANALYZE" runs list --limit 2 --format json >"$runs_list_output"
echo "validated_output=$runs_list_output"
assert_expr "$runs_list_output" "payload['count'] >= 1" "runs list should report at least one persisted run"
assert_expr "$runs_list_output" "payload['items'][0]['run_id'] == '$cache_manage_run_id'" "runs list should sort the latest run first"
assert_expr "$runs_list_output" "payload['items'][0]['is_latest'] is True" "runs list should mark the latest run"
assert_expr "$runs_list_output" "payload['items'][0]['task_type'] == 'summarize_method_logic'" "runs list should expose task type"
assert_expr "$runs_list_output" "payload['items'][0]['reused_step_count'] == 0" "runs list should expose reuse counters"
assert_expr "$runs_list_output" "payload['items'][0]['cache_hit_count'] == 0" "runs list should expose helper cache counters"

mkdir -p "$cache_manage_cache_root/v1/inputs/dex/invalid-dex-entry"
printf 'broken\n' >"$cache_manage_cache_root/v1/inputs/dex/invalid-dex-entry/orphan.txt"
mkdir -p "$cache_manage_cache_root/v1/export-and-scan/invalid-digest/invalid-entry"
printf 'broken\n' >"$cache_manage_cache_root/v1/export-and-scan/invalid-digest/invalid-entry/orphan.txt"
cache_manage_index_path="$cache_manage_work_root/runs/v1/reusable-step-index-v1.json"
python3 - "$cache_manage_index_path" <<'PY'
import json
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
payload = json.loads(path.read_text(encoding="utf-8"))
payload.setdefault("entries", {})["broken-cache-entry"] = {
    "run_id": "broken-run",
    "run_root": "/broken/run",
    "step_id": "step-broken",
    "step_kind": "export_and_scan",
    "step_result_path": "/broken/step-result.json",
    "release_tag": None,
    "updated_at": "1970-01-01T00:00:00+00:00",
}
path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY

cache_prune_output="$RESULT_DIR/cache_prune.json"
DEXCLUB_ANALYST_WORK_ROOT="$cache_manage_work_root" \
DEXCLUB_ANALYST_CACHE_DIR="$cache_manage_cache_root" \
python3 "$ANALYZE" cache prune --format json >"$cache_prune_output"
echo "validated_output=$cache_prune_output"
assert_expr "$cache_prune_output" "payload['inputs']['removed_entry_count'] >= 1" "cache prune should remove invalid input cache entries"
assert_expr "$cache_prune_output" "payload['export_and_scan']['removed_entry_count'] >= 1" "cache prune should remove invalid export-and-scan cache entries"
assert_expr "$cache_prune_output" "payload['reusable_steps']['removed_entry_count'] >= 1" "cache prune should remove invalid reusable-step-index entries"

cache_post_prune_output="$RESULT_DIR/cache_post_prune_inspect.json"
DEXCLUB_ANALYST_WORK_ROOT="$cache_manage_work_root" \
DEXCLUB_ANALYST_CACHE_DIR="$cache_manage_cache_root" \
python3 "$ANALYZE" cache inspect --format json >"$cache_post_prune_output"
echo "validated_output=$cache_post_prune_output"
assert_expr "$cache_post_prune_output" "payload['inputs']['invalid_entry_count'] == 0" "cache inspect after prune should report no invalid input cache entries"
assert_expr "$cache_post_prune_output" "payload['export_and_scan']['invalid_entry_count'] == 0" "cache inspect after prune should report no invalid export-and-scan cache entries"
assert_expr "$cache_post_prune_output" "payload['reusable_steps']['invalid_entry_count'] == 0" "cache inspect after prune should report no invalid reusable-step-index entries"

cache_clear_output="$RESULT_DIR/cache_clear.json"
DEXCLUB_ANALYST_WORK_ROOT="$cache_manage_work_root" \
DEXCLUB_ANALYST_CACHE_DIR="$cache_manage_cache_root" \
python3 "$ANALYZE" cache clear --format json >"$cache_clear_output"
echo "validated_output=$cache_clear_output"
assert_expr "$cache_clear_output" "'inputs' in payload['results'] and payload['results']['inputs']['cleared'] is True" "cache clear should clear inputs cache"
assert_expr "$cache_clear_output" "'export-and-scan' in payload['results'] and payload['results']['export-and-scan']['cleared'] is True" "cache clear should clear export-and-scan cache"
assert_expr "$cache_clear_output" "'reusable-steps' in payload['results'] and payload['results']['reusable-steps']['cleared'] is True" "cache clear should clear reusable-step-index"

cache_post_clear_output="$RESULT_DIR/cache_post_clear_inspect.json"
DEXCLUB_ANALYST_WORK_ROOT="$cache_manage_work_root" \
DEXCLUB_ANALYST_CACHE_DIR="$cache_manage_cache_root" \
python3 "$ANALYZE" cache inspect --format json >"$cache_post_clear_output"
echo "validated_output=$cache_post_clear_output"
assert_expr "$cache_post_clear_output" "payload['inputs']['stats']['exists'] is False" "cache inspect after clear should report no inputs cache root"
assert_expr "$cache_post_clear_output" "payload['export_and_scan']['stats']['exists'] is False" "cache inspect after clear should report no export-and-scan cache root"
assert_expr "$cache_post_clear_output" "payload['reusable_steps']['exists'] is False" "cache inspect after clear should report no reusable-step-index file"

parallel_run_work_root="$TMP_ROOT/parallel-run-work"
parallel_run_cache_root="$TMP_ROOT/parallel-run-cache"
parallel_run_input=$(cat <<JSON
{"input":["$SAMPLE_APK"],"method_anchor":{"class_name":"com.shadcn.ui.compose.MainActivity","method_name":"onCreate"}}
JSON
)
parallel_run_first_output="$RESULT_DIR/parallel_run_first.json"
parallel_run_second_output="$RESULT_DIR/parallel_run_second.json"
(
  DEXCLUB_ANALYST_WORK_ROOT="$parallel_run_work_root" \
  DEXCLUB_ANALYST_CACHE_DIR="$parallel_run_cache_root" \
  python3 "$ANALYZE" run --task-type summarize_method_logic --input-json "$parallel_run_input" >"$parallel_run_first_output"
) &
parallel_pid_one=$!
(
  DEXCLUB_ANALYST_WORK_ROOT="$parallel_run_work_root" \
  DEXCLUB_ANALYST_CACHE_DIR="$parallel_run_cache_root" \
  python3 "$ANALYZE" run --task-type summarize_method_logic --input-json "$parallel_run_input" >"$parallel_run_second_output"
) &
parallel_pid_two=$!
wait "$parallel_pid_one"
wait "$parallel_pid_two"
echo "validated_output=$parallel_run_first_output"
echo "validated_output=$parallel_run_second_output"
assert_expr "$parallel_run_first_output" "payload['status'] == 'ok'" "parallel summarize run one should succeed"
assert_expr "$parallel_run_second_output" "payload['status'] == 'ok'" "parallel summarize run two should succeed"
parallel_cache_inspect_output="$RESULT_DIR/parallel_cache_inspect.json"
DEXCLUB_ANALYST_WORK_ROOT="$parallel_run_work_root" \
DEXCLUB_ANALYST_CACHE_DIR="$parallel_run_cache_root" \
python3 "$ANALYZE" cache inspect --format json >"$parallel_cache_inspect_output"
echo "validated_output=$parallel_cache_inspect_output"
assert_expr "$parallel_cache_inspect_output" "payload['reusable_steps']['invalid_entry_count'] == 0" "parallel summarize runs should keep reusable-step-index valid"
assert_expr "$parallel_cache_inspect_output" "payload['export_and_scan']['invalid_entry_count'] == 0" "parallel summarize runs should keep export-and-scan cache valid"

overflow_input=$(cat <<JSON
{"input":["$SAMPLE_APK"],"string":"description"}
JSON
)
run_case "threshold_overflow" "search_methods_by_string" "$overflow_input"
assert_expr "$RESULT_DIR/threshold_overflow.json" "payload['status'] == 'ok'" "threshold overflow should still return ok with preview"
assert_expr "$RESULT_DIR/threshold_overflow.json" "payload['step_results'][0]['result']['truncated'] is True" "threshold overflow should mark preview truncated"
assert_expr "$RESULT_DIR/threshold_overflow.json" "(not payload['recommendations']) or payload['recommendations'][0]['reason'] == 'max_direct_hits_exceeded'" "threshold overflow should keep compatible narrowing guidance when recommendations are emitted"

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
  --input "$ambiguous_image_dex" \
  --class "androidx.compose.foundation.ImageKt" \
  --method "Image" \
  --method-descriptor "$exact_descriptor" \
  --language java \
  --mode summary \
  --output-format json >"$exact_java_export_output"
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

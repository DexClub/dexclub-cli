#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LAUNCHER="$SCRIPT_DIR/run_latest_release.sh"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
TMP_BASE_DIR="${DEXCLUB_LAUNCHER_VALIDATE_TMP_BASE:-$REPO_ROOT/.dexclub-cli/tmp}"

mkdir -p "$TMP_BASE_DIR"
TMP_ROOT="${DEXCLUB_LAUNCHER_VALIDATE_TMP:-$(mktemp -d "$TMP_BASE_DIR/dexclub-launcher.XXXXXX")}"
RESULT_DIR="$TMP_ROOT/results"
FAKE_BIN="$TMP_ROOT/fake-bin"

mkdir -p "$RESULT_DIR" "$FAKE_BIN"

assert_eq() {
  local actual="$1"
  local expected="$2"
  local message="$3"
  if [[ "$actual" != "$expected" ]]; then
    printf '%s\nexpected: %s\nactual:   %s\n' "$message" "$expected" "$actual" >&2
    exit 1
  fi
}

assert_exit_code() {
  local actual="$1"
  local expected="$2"
  local message="$3"
  if [[ "$actual" -ne "$expected" ]]; then
    printf '%s\nexpected exit: %s\nactual exit:   %s\n' "$message" "$expected" "$actual" >&2
    exit 1
  fi
}

assert_file_contains() {
  local file="$1"
  local needle="$2"
  local message="$3"
  if ! grep -Fq "$needle" "$file"; then
    printf '%s\nmissing: %s\nfile: %s\n' "$message" "$needle" "$file" >&2
    exit 1
  fi
}

run_case() {
  local name="$1"
  shift

  local stdout_file="$RESULT_DIR/$name.stdout"
  local stderr_file="$RESULT_DIR/$name.stderr"

  set +e
  (
    export DEXCLUB_CLI_TEST_UNAME_S="Linux"
    export DEXCLUB_CLI_TEST_UNAME_M="aarch64"
    "$@"
  ) >"$stdout_file" 2>"$stderr_file"
  local exit_code=$?
  set -e

  printf '%s\n' "$exit_code" >"$RESULT_DIR/$name.exit"
}

latest_stdout() {
  local name="$1"
  tr -d '\r' <"$RESULT_DIR/$name.stdout"
}

latest_exit_code() {
  local name="$1"
  cat "$RESULT_DIR/$name.exit"
}

make_cached_asset() {
  local cache_root="$1"
  local tag="$2"
  local platform="$3"
  local asset_dir="$cache_root/$tag/dexclub-cli-$platform"

  mkdir -p "$asset_dir/bin"
  printf '#!/bin/sh\nexit 0\n' >"$asset_dir/bin/cli"
  chmod +x "$asset_dir/bin/cli"
  printf 'fixture\n' >"$asset_dir/.archive-sha256"
}

cat >"$FAKE_BIN/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

args="$*"
if [[ "$args" == *"/releases/latest"* && "$args" == *"%{url_effective}"* ]]; then
  printf 'https://github.com/DexClub/dexclub-cli/releases/tag/v-test'
  exit 0
fi

if [[ "$args" == *"/releases/expanded_assets/v-test"* ]]; then
  cat <<'HTML'
<a href="/DexClub/dexclub-cli/releases/download/v-test/dexclub-cli-linux-arm64.zip">linux-arm64</a>
<a href="/DexClub/dexclub-cli/releases/download/v-test/dexclub-cli-linux-arm64.sha256">linux-arm64.sha256</a>
HTML
  exit 0
fi

printf 'unexpected curl call: %s\n' "$args" >&2
exit 99
EOF
chmod +x "$FAKE_BIN/curl"

run_case "glibc_runtime_class" env \
  DEXCLUB_CLI_TEST_LDD_VERSION="ldd (GNU libc) 2.39" \
  "$LAUNCHER" --print-runtime-class
assert_exit_code "$(latest_exit_code "glibc_runtime_class")" 0 "glibc runtime class should print successfully"
assert_eq "$(latest_stdout "glibc_runtime_class")" "glibc" "glibc runtime class should be detected"

run_case "glibc_platform" env \
  DEXCLUB_CLI_TEST_LDD_VERSION="ldd (GNU libc) 2.39" \
  "$LAUNCHER" --print-platform
assert_exit_code "$(latest_exit_code "glibc_platform")" 0 "glibc platform should print successfully"
assert_eq "$(latest_stdout "glibc_platform")" "linux-arm64" "glibc arm64 should map to linux-arm64"

run_case "bionic_runtime_class" env \
  DEXCLUB_CLI_TEST_LDD_VERSION="" \
  DEXCLUB_CLI_TEST_GETPROP_SDK="34" \
  "$LAUNCHER" --print-runtime-class
assert_exit_code "$(latest_exit_code "bionic_runtime_class")" 0 "bionic runtime class should print successfully"
assert_eq "$(latest_stdout "bionic_runtime_class")" "bionic" "bionic runtime class should be detected"

run_case "bionic_platform" env \
  DEXCLUB_CLI_TEST_LDD_VERSION="" \
  DEXCLUB_CLI_TEST_GETPROP_SDK="34" \
  "$LAUNCHER" --print-platform
assert_exit_code "$(latest_exit_code "bionic_platform")" 0 "bionic platform should print successfully"
assert_eq "$(latest_stdout "bionic_platform")" "android-arm64" "bionic arm64 should map to android-arm64"

run_case "musl_stop" env \
  DEXCLUB_CLI_TEST_LDD_VERSION="musl libc (aarch64)" \
  "$LAUNCHER" --print-platform
assert_exit_code "$(latest_exit_code "musl_stop")" 21 "musl should stop without fallback"
assert_file_contains "$RESULT_DIR/musl_stop.stderr" "detected runtime: musl" "musl stop should report runtime class"
assert_file_contains "$RESULT_DIR/musl_stop.stderr" "result: incompatible runtime target, stop without fallback" "musl stop should report incompatible runtime"

run_case "unknown_stop" env \
  DEXCLUB_CLI_TEST_LDD_VERSION="" \
  DEXCLUB_CLI_TEST_GETPROP_SDK="" \
  DEXCLUB_CLI_TEST_FS_ROOT="$TMP_ROOT/unknown-fs" \
  "$LAUNCHER" --print-platform
assert_exit_code "$(latest_exit_code "unknown_stop")" 21 "unknown runtime should stop without fallback"
assert_file_contains "$RESULT_DIR/unknown_stop.stderr" "detected runtime: unknown" "unknown stop should report runtime class"

cache_root="$TMP_ROOT/cache"
make_cached_asset "$cache_root" "v-local" "linux-arm64"

run_case "cache_glibc_tag" env \
  DEXCLUB_CLI_CACHE_DIR="$cache_root" \
  DEXCLUB_CLI_TEST_LDD_VERSION="ldd (GNU libc) 2.39" \
  "$LAUNCHER" --print-latest-tag
assert_exit_code "$(latest_exit_code "cache_glibc_tag")" 0 "glibc should reuse linux-arm64 cache"
assert_eq "$(latest_stdout "cache_glibc_tag")" "v-local" "glibc should resolve cached linux-arm64 tag"

run_case "cache_bionic_tag" env \
  DEXCLUB_CLI_CACHE_DIR="$cache_root" \
  DEXCLUB_CLI_TEST_LDD_VERSION="" \
  DEXCLUB_CLI_TEST_GETPROP_SDK="34" \
  "$LAUNCHER" --print-latest-tag
assert_exit_code "$(latest_exit_code "cache_bionic_tag")" 1 "bionic must not reuse linux-arm64 cache"

run_case "missing_android_asset" env \
  PATH="$FAKE_BIN:$PATH" \
  DEXCLUB_CLI_CACHE_DIR="$TMP_ROOT/remote-cache" \
  DEXCLUB_CLI_TEST_LDD_VERSION="" \
  DEXCLUB_CLI_TEST_GETPROP_SDK="34" \
  "$LAUNCHER" --update-cache --prepare-only
assert_exit_code "$(latest_exit_code "missing_android_asset")" 22 "missing android-arm64 asset should stop with structured diagnostics"
assert_file_contains "$RESULT_DIR/missing_android_asset.stderr" "detected runtime: bionic" "missing asset should report detected runtime"
assert_file_contains "$RESULT_DIR/missing_android_asset.stderr" "expected artifact class: android-arm64" "missing asset should report expected artifact"
assert_file_contains "$RESULT_DIR/missing_android_asset.stderr" "available artifact class: linux-arm64" "missing asset should report available artifact classes"
assert_file_contains "$RESULT_DIR/missing_android_asset.stderr" "result: expected artifact missing, stop without fallback" "missing asset should report no fallback"

printf 'validation=passed\n'
printf 'results_dir=%s\n' "$RESULT_DIR"

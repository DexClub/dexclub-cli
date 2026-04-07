#!/usr/bin/env bash
set -euo pipefail

REPO="${DEXCLUB_CLI_GITHUB_REPO:-DexClub/dexclub-cli}"
REMOTE_FAILURE_LIMIT=2

unsupported_platform() {
  printf '\u8BE5 skill \u65E0\u6CD5\u5728\u5F53\u524D\u7CFB\u7EDF\u4E0A\u4F7F\u7528\n' >&2
}

resolve_cache_root() {
  local cache_root
  cache_root="${DEXCLUB_CLI_CACHE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/dexclub-cli/releases}"
  case "$cache_root" in
    /*) ;;
    *) cache_root="$HOME/$cache_root" ;;
  esac
  printf '%s\n' "$cache_root"
}

CACHE_ROOT="$(resolve_cache_root)"
STATE_DIR="${CACHE_ROOT}/.state"

repo_state_key() {
  printf '%s\n' "$REPO" | sed 's/[^A-Za-z0-9._-]/_/g'
}

detect_os() {
  local uname_s
  uname_s="$(uname -s 2>/dev/null || printf 'unknown')"
  case "$uname_s" in
    Linux) printf 'linux\n' ;;
    Darwin) printf 'macos\n' ;;
    MINGW*|MSYS*|CYGWIN*|Windows_NT) printf 'windows\n' ;;
    *)
      unsupported_platform
      exit 20
      ;;
  esac
}

detect_arch() {
  local uname_m
  uname_m="$(uname -m 2>/dev/null || printf 'unknown')"
  case "$uname_m" in
    x86_64|amd64) printf 'x64\n' ;;
    aarch64|arm64) printf 'arm64\n' ;;
    *)
      unsupported_platform
      exit 20
      ;;
  esac
}

compute_sha256() {
  local file_path="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$file_path" | awk '{print $1}'
    return 0
  fi
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$file_path" | awk '{print $1}'
    return 0
  fi
  if command -v openssl >/dev/null 2>&1; then
    openssl dgst -sha256 "$file_path" | awk '{print $NF}'
    return 0
  fi
  return 1
}

download_file() {
  local url="$1"
  local output_path="$2"
  local tmp_path
  tmp_path="${output_path}.tmp"
  curl -fsSL --retry 3 -H 'User-Agent: dexclub-cli-release-launcher' "$url" -o "$tmp_path"
  mv "$tmp_path" "$output_path"
}

extract_zip() {
  local zip_path="$1"
  local output_dir="$2"
  if command -v unzip >/dev/null 2>&1; then
    unzip -q "$zip_path" -d "$output_dir"
    return 0
  fi
  if command -v bsdtar >/dev/null 2>&1; then
    bsdtar -xf "$zip_path" -C "$output_dir"
    return 0
  fi
  if command -v jar >/dev/null 2>&1; then
    (
      cd "$output_dir"
      jar xf "$zip_path"
    )
    return 0
  fi
  printf 'No zip extractor is available. Install unzip, bsdtar, or jar.\n' >&2
  exit 1
}

find_launcher() {
  local extract_dir="$1"
  local launcher

  launcher="$(
    find "$extract_dir" -type f -path '*/bin/*' ! -name '*.bat' 2>/dev/null | LC_ALL=C sort | head -n 1
  )"
  if [ -n "$launcher" ]; then
    printf '%s\n' "$launcher"
    return 0
  fi

  launcher="$(
    find "$extract_dir" -type f -path '*/bin/*.bat' 2>/dev/null | LC_ALL=C sort | head -n 1
  )"
  if [ -n "$launcher" ]; then
    printf '%s\n' "$launcher"
    return 0
  fi

  printf 'Unable to find a CLI launcher under %s\n' "$extract_dir" >&2
  exit 1
}

latest_release_tag_from_api() {
  local response tag
  if ! response="$(curl -fsSL -H 'Accept: application/vnd.github+json' -H 'User-Agent: dexclub-cli-release-launcher' "https://api.github.com/repos/${REPO}/releases/latest" 2>/dev/null)"; then
    return 1
  fi
  tag="$(
    printf '%s' "$response" |
      tr -d '\n' |
      sed -n 's/.*"tag_name"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p'
  )"
  [ -n "$tag" ] || return 1
  printf '%s\n' "$tag"
}

latest_release_tag() {
  local tag
  if tag="$(latest_release_tag_from_api)"; then
    printf '%s\n' "$tag"
    return 0
  fi
  printf 'Unable to resolve the latest GitHub Release tag from the GitHub API.\n' >&2
  return 1
}

print_local_selected_tag() {
  local platform="$1"
  local tag

  if tag="$(read_selected_tag "$platform")" && [ -n "$tag" ] && has_cached_asset "$platform" "$tag"; then
    printf '%s\n' "$tag"
    return 0
  fi

  if tag="$(find_cached_tag "$platform" 2>/dev/null)"; then
    printf '%s\n' "$tag"
    return 0
  fi

  return 1
}

selected_tag_file() {
  local platform="$1"
  printf '%s/%s.%s.tag\n' "$STATE_DIR" "$(repo_state_key)" "$platform"
}

remote_failure_file() {
  local platform="$1"
  printf '%s/%s.%s.remote_failures\n' "$STATE_DIR" "$(repo_state_key)" "$platform"
}

write_selected_tag() {
  local platform="$1"
  local tag="$2"
  mkdir -p "$STATE_DIR"
  printf '%s\n' "$tag" > "$(selected_tag_file "$platform")"
}

read_selected_tag() {
  local platform="$1"
  local tag_file
  tag_file="$(selected_tag_file "$platform")"
  if [ -f "$tag_file" ]; then
    awk 'NF { print $1; exit }' "$tag_file"
  fi
}

read_remote_failures() {
  local platform="$1"
  local failure_file
  failure_file="$(remote_failure_file "$platform")"
  if [ -f "$failure_file" ]; then
    awk 'NF { print $1; exit }' "$failure_file"
    return 0
  fi
  printf '0\n'
}

write_remote_failures() {
  local platform="$1"
  local count="$2"
  mkdir -p "$STATE_DIR"
  printf '%s\n' "$count" > "$(remote_failure_file "$platform")"
}

reset_remote_failures() {
  local platform="$1"
  write_remote_failures "$platform" 0
}

record_remote_failure() {
  local platform="$1"
  local reason="$2"
  local count
  count="$(read_remote_failures "$platform")"
  count="$((count + 1))"
  write_remote_failures "$platform" "$count"
  printf '%s\n' "$reason" >&2
  if [ "$count" -lt "$REMOTE_FAILURE_LIMIT" ]; then
    printf 'Remote access failed (%s/%s). Retry after repository or network access is restored.\n' "$count" "$REMOTE_FAILURE_LIMIT" >&2
  else
    printf 'Remote access failed (%s/%s). Further remote checks are now disabled until --reset-remote-failures is used.\n' "$count" "$REMOTE_FAILURE_LIMIT" >&2
  fi
  return 30
}

check_remote_access_allowed() {
  local platform="$1"
  local context="$2"
  local count
  count="$(read_remote_failures "$platform")"
  if [ "$count" -ge "$REMOTE_FAILURE_LIMIT" ]; then
    if [ "$context" = "no-cache" ]; then
      printf 'No compatible local cache is available, and remote access is disabled after %s failed attempts. Use --reset-remote-failures after connectivity is restored.\n' "$REMOTE_FAILURE_LIMIT" >&2
    else
      printf 'Remote access is disabled after %s failed attempts. Use --reset-remote-failures after connectivity is restored.\n' "$REMOTE_FAILURE_LIMIT" >&2
    fi
    return 30
  fi
  return 0
}

asset_paths() {
  local platform="$1"
  local tag="$2"
  local asset_base="dexclub-cli-${platform}"
  local release_dir="${CACHE_ROOT}/${tag}"
  printf '%s\n' \
    "${asset_base}" \
    "${release_dir}" \
    "${release_dir}/${asset_base}.zip" \
    "${release_dir}/${asset_base}.sha256" \
    "${release_dir}/${asset_base}" \
    "${release_dir}/${asset_base}.tmp" \
    "${release_dir}/${asset_base}/.archive-sha256"
}

has_cached_asset() {
  local platform="$1"
  local tag="$2"
  local asset_base release_dir zip_path sha_path extract_dir extract_tmp digest_marker
  mapfile -t _paths < <(asset_paths "$platform" "$tag")
  asset_base="${_paths[0]}"
  release_dir="${_paths[1]}"
  zip_path="${_paths[2]}"
  sha_path="${_paths[3]}"
  extract_dir="${_paths[4]}"
  extract_tmp="${_paths[5]}"
  digest_marker="${_paths[6]}"

  if [ -f "$zip_path" ] && [ -f "$sha_path" ]; then
    return 0
  fi
  if [ -d "$extract_dir" ] && [ -f "$digest_marker" ]; then
    return 0
  fi
  return 1
}

ensure_cached_asset_ready() {
  local platform="$1"
  local tag="$2"
  local asset_base release_dir zip_path sha_path extract_dir extract_tmp digest_marker expected_sha actual_sha
  mapfile -t _paths < <(asset_paths "$platform" "$tag")
  asset_base="${_paths[0]}"
  release_dir="${_paths[1]}"
  zip_path="${_paths[2]}"
  sha_path="${_paths[3]}"
  extract_dir="${_paths[4]}"
  extract_tmp="${_paths[5]}"
  digest_marker="${_paths[6]}"

  mkdir -p "$release_dir"

  if [ ! -f "$zip_path" ] || [ ! -f "$sha_path" ]; then
    return 1
  fi

  expected_sha="$(awk '{print $1}' "$sha_path")"
  if [ -n "$expected_sha" ] && actual_sha="$(compute_sha256 "$zip_path" 2>/dev/null)"; then
    if [ "$actual_sha" != "$expected_sha" ]; then
      return 1
    fi
  fi

  if [ -f "$digest_marker" ] && [ "$(cat "$digest_marker")" = "$expected_sha" ]; then
    printf '%s\n' "$extract_dir"
    return 0
  fi

  rm -rf "$extract_tmp" "$extract_dir"
  mkdir -p "$extract_tmp"
  extract_zip "$zip_path" "$extract_tmp"
  printf '%s\n' "$expected_sha" > "$extract_tmp/.archive-sha256"
  mv "$extract_tmp" "$extract_dir"
  printf '%s\n' "$extract_dir"
}

ensure_remote_asset_ready() {
  local platform="$1"
  local tag="$2"
  local asset_base release_dir zip_path sha_path extract_dir extract_tmp digest_marker expanded_assets_url expanded_assets expected_sha actual_sha
  mapfile -t _paths < <(asset_paths "$platform" "$tag")
  asset_base="${_paths[0]}"
  release_dir="${_paths[1]}"
  zip_path="${_paths[2]}"
  sha_path="${_paths[3]}"
  extract_dir="${_paths[4]}"
  extract_tmp="${_paths[5]}"
  digest_marker="${_paths[6]}"
  expanded_assets_url="https://github.com/${REPO}/releases/expanded_assets/${tag}"

  mkdir -p "$release_dir"

  if ! expanded_assets="$(curl -fsSL -H 'User-Agent: dexclub-cli-release-launcher' "$expanded_assets_url")"; then
    return 1
  fi
  if ! printf '%s' "$expanded_assets" | grep -Fq "${asset_base}.zip"; then
    unsupported_platform
    exit 20
  fi

  if [ ! -f "$zip_path" ]; then
    download_file "https://github.com/${REPO}/releases/download/${tag}/${asset_base}.zip" "$zip_path" || return 1
  fi

  if [ ! -f "$sha_path" ]; then
    download_file "https://github.com/${REPO}/releases/download/${tag}/${asset_base}.sha256" "$sha_path" || return 1
  fi

  expected_sha="$(awk '{print $1}' "$sha_path")"
  if [ -n "$expected_sha" ] && actual_sha="$(compute_sha256 "$zip_path" 2>/dev/null)"; then
    if [ "$actual_sha" != "$expected_sha" ]; then
      download_file "https://github.com/${REPO}/releases/download/${tag}/${asset_base}.zip" "$zip_path" || return 1
      actual_sha="$(compute_sha256 "$zip_path")"
      if [ "$actual_sha" != "$expected_sha" ]; then
        printf 'Checksum verification failed for %s\n' "$zip_path" >&2
        exit 1
      fi
    fi
  fi

  if [ -f "$digest_marker" ] && [ "$(cat "$digest_marker")" = "$expected_sha" ]; then
    printf '%s\n' "$extract_dir"
    return 0
  fi

  rm -rf "$extract_tmp" "$extract_dir"
  mkdir -p "$extract_tmp"
  extract_zip "$zip_path" "$extract_tmp"
  printf '%s\n' "$expected_sha" > "$extract_tmp/.archive-sha256"
  mv "$extract_tmp" "$extract_dir"
  printf '%s\n' "$extract_dir"
}

find_cached_tag() {
  local platform="$1"
  local preferred_tag candidate

  preferred_tag="$(read_selected_tag "$platform" || true)"
  if [ -n "$preferred_tag" ] && has_cached_asset "$platform" "$preferred_tag"; then
    printf '%s\n' "$preferred_tag"
    return 0
  fi

  while IFS= read -r candidate; do
    [ -n "$candidate" ] || continue
    if has_cached_asset "$platform" "$candidate"; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done < <(
    find "$CACHE_ROOT" -mindepth 1 -maxdepth 1 -type d ! -name '.state' -printf '%f\n' 2>/dev/null | LC_ALL=C sort -Vr
  )

  return 1
}

resolve_asset_dir() {
  local platform="$1"
  local update_cache="$2"
  local tag extract_dir code

  if [ "$update_cache" = "1" ]; then
    check_remote_access_allowed "$platform" "update" || return $?
    if ! tag="$(latest_release_tag)"; then
      record_remote_failure "$platform" "Unable to refresh the cached release from GitHub Release."
      return $?
    fi
    if ! extract_dir="$(ensure_remote_asset_ready "$platform" "$tag")"; then
      code=$?
      if [ "$code" -eq 20 ]; then
        return "$code"
      fi
      record_remote_failure "$platform" "Unable to refresh the cached release from GitHub Release."
      return $?
    fi
    reset_remote_failures "$platform"
    write_selected_tag "$platform" "$tag"
    printf '%s\n' "$extract_dir"
    return 0
  fi

  if tag="$(find_cached_tag "$platform" 2>/dev/null)"; then
    extract_dir="$(ensure_cached_asset_ready "$platform" "$tag")" || return $?
    write_selected_tag "$platform" "$tag"
    printf '%s\n' "$extract_dir"
    return 0
  fi

  check_remote_access_allowed "$platform" "no-cache" || return $?
  if ! tag="$(latest_release_tag)"; then
    record_remote_failure "$platform" "Unable to fetch the initial cached release from GitHub Release."
    return $?
  fi
  if ! extract_dir="$(ensure_remote_asset_ready "$platform" "$tag")"; then
    code=$?
    if [ "$code" -eq 20 ]; then
      return "$code"
    fi
    record_remote_failure "$platform" "Unable to fetch the initial cached release from GitHub Release."
    return $?
  fi
  reset_remote_failures "$platform"
  write_selected_tag "$platform" "$tag"
  printf '%s\n' "$extract_dir"
}

print_usage() {
  cat <<'EOF'
Usage:
  run_latest_release.sh [--print-cache-path]
  run_latest_release.sh [--print-platform]
  run_latest_release.sh [--print-latest-tag]
  run_latest_release.sh [--prepare-only]
  run_latest_release.sh [--print-launcher]
  run_latest_release.sh [--update-cache]
  run_latest_release.sh [--reset-remote-failures]
  run_latest_release.sh -- [dexclub-cli args...]

Environment:
  DEXCLUB_CLI_GITHUB_REPO   Override the GitHub repository (default: DexClub/dexclub-cli)
  DEXCLUB_CLI_CACHE_DIR     Override the cache root
EOF
}

main() {
  local os_name arch_name platform extract_dir launcher
  local update_cache=0
  local prepare_only=0
  local print_launcher_flag=0
  local reset_remote_failures_flag=0

  os_name="$(detect_os)"
  arch_name="$(detect_arch)"
  platform="${os_name}-${arch_name}"

  while [ $# -gt 0 ]; do
    case "$1" in
      --help|-h)
        print_usage
        exit 0
        ;;
      --print-cache-path)
        printf '%s\n' "$CACHE_ROOT"
        exit 0
        ;;
      --print-platform)
        printf '%s\n' "$platform"
        exit 0
        ;;
      --print-latest-tag)
        print_local_selected_tag "$platform"
        exit $?
        ;;
      --prepare-only)
        prepare_only=1
        shift
        ;;
      --print-launcher)
        print_launcher_flag=1
        shift
        ;;
      --update-cache|--refresh-cache)
        update_cache=1
        shift
        ;;
      --reset-remote-failures)
        reset_remote_failures_flag=1
        shift
        ;;
      --)
        shift
        break
        ;;
      *)
        printf 'Unknown option: %s\n' "$1" >&2
        print_usage >&2
        exit 1
        ;;
    esac
  done

  if [ "$reset_remote_failures_flag" = "1" ]; then
    reset_remote_failures "$platform"
  fi

  extract_dir="$(resolve_asset_dir "$platform" "$update_cache")" || exit $?

  if [ "$prepare_only" = "1" ]; then
    printf '%s\n' "$extract_dir"
    exit 0
  fi

  launcher="$(find_launcher "$extract_dir")"
  if [ ! -x "$launcher" ] && [ "${launcher##*.}" != "bat" ]; then
    chmod +x "$launcher"
  fi

  if [ "$print_launcher_flag" = "1" ]; then
    printf '%s\n' "$launcher"
    exit 0
  fi

  exec "$launcher" "$@"
}

main "$@"

#!/usr/bin/env bash
# 本脚本承载 Android 宿主或 `hubmobile` 集成中与 `build_aar` 相关的构建/验证流程。

set -euo pipefail

TARGET="${1:-android/arm64}"
JAVA_PKG="${2:-com.myflowhub.gomobile}"
OUT_FILE="${3:-app/libs/myflowhub.aar}"
ANDROID_API="${4:-${ANDROID_API:-26}}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "Build AAR via gomobile"
echo "  Target : ${TARGET}"
echo "  AndroidApi: ${ANDROID_API}"
echo "  JavaPkg: ${JAVA_PKG}"
echo "  OutFile: ${OUT_FILE}"

export GOWORK=off

if ! [[ "${ANDROID_API}" =~ ^[0-9]+$ ]]; then
  echo "AndroidApi 非法：${ANDROID_API}（期望为正整数）" >&2
  exit 1
fi
if (( ANDROID_API < 21 )); then
  echo "AndroidApi 过低：${ANDROID_API}（NDK r26 仅支持 21..34，且本项目 minSdk=26）" >&2
  exit 1
fi

if [[ -z "${ANDROID_HOME:-}" && -n "${ANDROID_SDK_ROOT:-}" ]]; then
  export ANDROID_HOME="${ANDROID_SDK_ROOT}"
fi

if [[ -z "${ANDROID_NDK_HOME:-}" && -n "${ANDROID_SDK_ROOT:-}" ]]; then
  NDK_CANDIDATE="$(ls -1d "${ANDROID_SDK_ROOT}/ndk/"* 2>/dev/null | sort -V | tail -n 1 || true)"
  if [[ -n "${NDK_CANDIDATE}" ]]; then
    export ANDROID_NDK_HOME="${NDK_CANDIDATE}"
  fi
fi

ensure_go_bin_in_path() {
  local go_bin
  go_bin="$(go env GOPATH)/bin"
  case ":${PATH}:" in
    *":${go_bin}:"*) ;;
    *) export PATH="${go_bin}:${PATH}" ;;
  esac
}

resolve_x_mobile_version() {
  local mobile_version
  pushd "${REPO_ROOT}/hubmobile" >/dev/null
  mobile_version="$(GOWORK=off go list -m -f '{{.Version}}' golang.org/x/mobile 2>/dev/null || true)"
  popd >/dev/null
  if [[ -n "${mobile_version}" && "${mobile_version}" != "(devel)" ]]; then
    printf '%s' "${mobile_version}"
    return 0
  fi
  return 1
}

install_gomobile_tools() {
  local mobile_version
  if mobile_version="$(resolve_x_mobile_version)"; then
    echo "Installing gomobile/gobind: ${mobile_version}"
    GOWORK=off go install "golang.org/x/mobile/cmd/gomobile@${mobile_version}"
    GOWORK=off go install "golang.org/x/mobile/cmd/gobind@${mobile_version}"
  else
    echo "WARN: 无法从 hubmobile/go.mod 解析 golang.org/x/mobile 版本，fallback 到 latest" >&2
    GOWORK=off go install golang.org/x/mobile/cmd/gomobile@latest
    GOWORK=off go install golang.org/x/mobile/cmd/gobind@latest
  fi
  ensure_go_bin_in_path
}

ensure_go_bin_in_path
if ! command -v gomobile >/dev/null 2>&1 || ! command -v gobind >/dev/null 2>&1; then
  echo "gomobile/gobind not found, installing..."
  install_gomobile_tools
fi
gomobile version || true

mkdir -p "$(dirname "${REPO_ROOT}/${OUT_FILE}")"

echo "Running: gomobile init"
gomobile init

pushd "${REPO_ROOT}/hubmobile" >/dev/null
echo "Running: gomobile bind"
gomobile bind -target "${TARGET}" -androidapi "${ANDROID_API}" -javapkg "${JAVA_PKG}" -o "${REPO_ROOT}/${OUT_FILE}" .
popd >/dev/null

echo "Done: ${OUT_FILE}"


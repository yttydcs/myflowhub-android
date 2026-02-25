#!/usr/bin/env bash
set -euo pipefail

TARGET="${1:-android/arm64}"
JAVA_PKG="${2:-com.myflowhub.native}"
OUT_FILE="${3:-app/libs/myflowhub.aar}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "Build AAR via gomobile"
echo "  Target : ${TARGET}"
echo "  JavaPkg: ${JAVA_PKG}"
echo "  OutFile: ${OUT_FILE}"

export GOWORK=off

if [[ -z "${ANDROID_HOME:-}" && -n "${ANDROID_SDK_ROOT:-}" ]]; then
  export ANDROID_HOME="${ANDROID_SDK_ROOT}"
fi

if [[ -z "${ANDROID_NDK_HOME:-}" && -n "${ANDROID_SDK_ROOT:-}" ]]; then
  NDK_CANDIDATE="$(ls -1d "${ANDROID_SDK_ROOT}/ndk/"* 2>/dev/null | sort -V | tail -n 1 || true)"
  if [[ -n "${NDK_CANDIDATE}" ]]; then
    export ANDROID_NDK_HOME="${NDK_CANDIDATE}"
  fi
fi

if ! command -v gomobile >/dev/null 2>&1; then
  echo "gomobile not found, installing..."
  go install golang.org/x/mobile/cmd/gomobile@latest
fi

mkdir -p "$(dirname "${REPO_ROOT}/${OUT_FILE}")"

echo "Running: gomobile init"
gomobile init

pushd "${REPO_ROOT}/hubmobile" >/dev/null
echo "Running: gomobile bind"
gomobile bind -target "${TARGET}" -javapkg "${JAVA_PKG}" -o "${REPO_ROOT}/${OUT_FILE}" .
popd >/dev/null

echo "Done: ${OUT_FILE}"


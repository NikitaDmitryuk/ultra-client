#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="$ROOT_DIR/sing-box-build"
SRC_DIR="$BUILD_DIR/sing-box-src"
OUT_DIR="$BUILD_DIR/output/android"

SING_BOX_VERSION="${SING_BOX_VERSION:-v1.13.13}"
SING_BOX_REPO="${SING_BOX_REPO:-https://github.com/SagerNet/sing-box.git}"
ANDROID_TARGET="${SING_BOX_ANDROID_TARGET:-}"
export GOTOOLCHAIN="${GOTOOLCHAIN:-go1.24.7}"

mkdir -p "$BUILD_DIR" "$OUT_DIR"

if [[ ! -d "$SRC_DIR/.git" ]]; then
  git clone --depth 1 --branch "$SING_BOX_VERSION" "$SING_BOX_REPO" "$SRC_DIR"
else
  git -C "$SRC_DIR" fetch --depth 1 origin "$SING_BOX_VERSION"
  git -C "$SRC_DIR" checkout --detach FETCH_HEAD
fi

if [[ -z "${ANDROID_HOME:-}" ]]; then
  echo "ANDROID_HOME is required" >&2
  exit 1
fi

if [[ -z "${ANDROID_NDK_HOME:-}" && -n "${ANDROID_NDK_ROOT:-}" ]]; then
  export ANDROID_NDK_HOME="$ANDROID_NDK_ROOT"
fi

if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
  latest_ndk="$(find "$ANDROID_HOME/ndk" -maxdepth 1 -mindepth 1 -type d 2>/dev/null | sort -V | tail -1 || true)"
  if [[ -n "$latest_ndk" ]]; then
    export ANDROID_NDK_HOME="$latest_ndk"
  fi
fi

if [[ -z "${ANDROID_NDK_HOME:-}" && -d "$ANDROID_HOME/ndk-bundle" ]]; then
  export ANDROID_NDK_HOME="$ANDROID_HOME/ndk-bundle"
fi

if [[ -z "${ANDROID_NDK_HOME:-}" || ! -d "$ANDROID_NDK_HOME" ]]; then
  echo "ANDROID_NDK_HOME is required or Android NDK must be installed under \$ANDROID_HOME/ndk" >&2
  exit 1
fi

export ANDROID_NDK_ROOT="$ANDROID_NDK_HOME"
echo "Using Android NDK: $ANDROID_NDK_HOME"

if ! command -v go >/dev/null 2>&1; then
  echo "Go is required to build sing-box libbox" >&2
  exit 1
fi

export PATH="$(go env GOPATH)/bin:$PATH"
go install -v github.com/sagernet/gomobile/cmd/gomobile@v0.1.4
go install -v github.com/sagernet/gomobile/cmd/gobind@v0.1.4

cd "$SRC_DIR"
args=(./cmd/internal/build_libbox -target android)
if [[ -n "$ANDROID_TARGET" ]]; then
  args+=(-platform "$ANDROID_TARGET")
fi
go run "${args[@]}"

if [[ ! -f "$SRC_DIR/libbox.aar" ]]; then
  echo "sing-box build completed but libbox.aar was not produced" >&2
  exit 1
fi

cp "$SRC_DIR/libbox.aar" "$OUT_DIR/SingBoxCore.aar"
mkdir -p "$ROOT_DIR/androidApp/libs"
cp "$OUT_DIR/SingBoxCore.aar" "$ROOT_DIR/androidApp/libs/SingBoxCore.aar"
echo "Built $OUT_DIR/SingBoxCore.aar"

#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="$ROOT_DIR/sing-box-build"
SRC_DIR="$BUILD_DIR/sing-box-src"
OUT_DIR="$BUILD_DIR/output/desktop"

SING_BOX_VERSION="${SING_BOX_VERSION:-v1.13.13}"
SING_BOX_REPO="${SING_BOX_REPO:-https://github.com/SagerNet/sing-box.git}"
TARGET_OS="${SING_BOX_DESKTOP_GOOS:-$(go env GOOS)}"
TARGET_ARCH="${SING_BOX_DESKTOP_GOARCH:-$(go env GOARCH)}"

mkdir -p "$BUILD_DIR" "$OUT_DIR"

if [[ ! -d "$SRC_DIR/.git" ]]; then
  git clone --depth 1 --branch "$SING_BOX_VERSION" "$SING_BOX_REPO" "$SRC_DIR"
else
  git -C "$SRC_DIR" fetch --depth 1 origin "$SING_BOX_VERSION"
  git -C "$SRC_DIR" checkout --detach FETCH_HEAD
fi

if ! command -v go >/dev/null 2>&1; then
  echo "Go is required to build sing-box" >&2
  exit 1
fi

cd "$SRC_DIR"
output_name="sing-box"
if [[ "$TARGET_OS" == "windows" ]]; then
  output_name="sing-box.exe"
fi

GOOS="$TARGET_OS" GOARCH="$TARGET_ARCH" go build \
  -trimpath \
  -ldflags="-s -w" \
  -o "$OUT_DIR/$output_name" \
  ./cmd/sing-box
echo "Built $OUT_DIR/$output_name"

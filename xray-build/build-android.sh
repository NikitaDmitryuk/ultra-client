#!/usr/bin/env bash
# Requires: Go 1.23+, Android NDK r27+, ANDROID_HOME, ANDROID_NDK_HOME set
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="$SCRIPT_DIR/xray-core-src"
OUTPUT_DIR="$SCRIPT_DIR/output/android"

mkdir -p "$OUTPUT_DIR"

echo "==> Installing gomobile..."
go install golang.org/x/mobile/cmd/gomobile@latest
go install golang.org/x/mobile/cmd/gobind@latest
export PATH="$PATH:$(go env GOPATH)/bin"

echo "==> Initializing gomobile..."
gomobile init

echo "==> Building Android AAR (arm64)..."
cd "$SRC_DIR/libXray"

gomobile bind \
    -o "$OUTPUT_DIR/XrayCore.aar" \
    -target "android/arm64" \
    -androidapi 26 \
    -ldflags="-buildid= -s -w" \
    -trimpath \
    github.com/xtls/libxray

echo "==> Android AAR built: $OUTPUT_DIR/XrayCore.aar"

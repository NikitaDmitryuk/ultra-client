#!/usr/bin/env bash
# Requires: Go 1.26.2+, Python 3.9+, Android NDK r27+, ANDROID_HOME and ANDROID_NDK_HOME set.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="$SCRIPT_DIR/xray-core-src"
OUTPUT_DIR="$SCRIPT_DIR/output/android"

mkdir -p "$OUTPUT_DIR"

command -v go      >/dev/null 2>&1 || { echo "ERROR: Go not found. Install from https://go.dev/dl"; exit 1; }
command -v git     >/dev/null 2>&1 || { echo "ERROR: git not found"; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo "ERROR: python3 not found"; exit 1; }
[ -n "${ANDROID_HOME:-}"     ] || { echo "ERROR: ANDROID_HOME not set"; exit 1; }
[ -n "${ANDROID_NDK_HOME:-}" ] || { echo "ERROR: ANDROID_NDK_HOME not set"; exit 1; }

export PATH="$PATH:$(go env GOPATH)/bin"

echo "==> Cloning / updating libXray..."
if [ ! -d "$SRC_DIR/libXray/.git" ]; then
    git clone --depth 1 https://github.com/XTLS/libXray.git "$SRC_DIR/libXray"
else
    git -C "$SRC_DIR/libXray" pull --ff-only
fi

echo "==> Building Android AAR via libXray build script..."
cd "$SRC_DIR/libXray"
python3 build/main.py android

cp "$SRC_DIR/libXray/libXray.aar" "$OUTPUT_DIR/XrayCore.aar"
echo "==> Done: $OUTPUT_DIR/XrayCore.aar"

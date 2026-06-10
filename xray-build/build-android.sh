#!/usr/bin/env bash
# Requires: Go 1.26.2+, Python 3.9+, Android NDK r27+.
# ANDROID_HOME / ANDROID_NDK_HOME можно не задавать: ниже подставляются типичные пути SDK/NDK.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="$SCRIPT_DIR/xray-core-src"
OUTPUT_DIR="$SCRIPT_DIR/output/android"

mkdir -p "$OUTPUT_DIR"

# Типичный SDK: Android Studio на macOS и Linux
if [ -z "${ANDROID_HOME:-}" ]; then
  for candidate in "${HOME}/Library/Android/sdk" "${HOME}/Android/Sdk"; do
    if [ -d "$candidate" ]; then
      export ANDROID_HOME="$candidate"
      echo "==> ANDROID_HOME was unset; using $ANDROID_HOME"
      break
    fi
  done
fi

# Последняя по сортировке версии NDK в sdk/ndk/*
if [ -z "${ANDROID_NDK_HOME:-}" ] && [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME/ndk" ]; then
  ANDROID_NDK_HOME="$(find "$ANDROID_HOME/ndk" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -1)"
  if [ -n "$ANDROID_NDK_HOME" ]; then
    export ANDROID_NDK_HOME
    echo "==> ANDROID_NDK_HOME was unset; using $ANDROID_NDK_HOME"
  fi
fi

command -v go      >/dev/null 2>&1 || { echo "ERROR: Go not found. Install from https://go.dev/dl"; exit 1; }
command -v git     >/dev/null 2>&1 || { echo "ERROR: git not found"; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo "ERROR: python3 not found"; exit 1; }
[ -n "${ANDROID_HOME:-}"     ] || {
  echo "ERROR: ANDROID_HOME not set and no default SDK at ~/Library/Android/sdk or ~/Android/Sdk"
  exit 1
}
[ -n "${ANDROID_NDK_HOME:-}" ] || {
  echo "ERROR: ANDROID_NDK_HOME not set and no NDK under \$ANDROID_HOME/ndk (install NDK in Android Studio SDK Manager)"
  exit 1
}

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

#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
SRC_DIR="$SCRIPT_DIR/xray-core-src"

echo "==> Checking prerequisites..."
command -v go  >/dev/null 2>&1 || { echo "Go not found. Install from https://go.dev/dl"; exit 1; }
command -v git >/dev/null 2>&1 || { echo "git not found"; exit 1; }

echo "==> Cloning / updating Xray-core sources..."
mkdir -p "$SRC_DIR"

if [ ! -d "$SRC_DIR/Xray-core" ]; then
    git clone --depth 1 https://github.com/XTLS/Xray-core.git "$SRC_DIR/Xray-core"
else
    git -C "$SRC_DIR/Xray-core" pull --ff-only
fi

if [ ! -d "$SRC_DIR/libXray" ]; then
    git clone --depth 1 https://github.com/XTLS/libXray.git "$SRC_DIR/libXray"
else
    git -C "$SRC_DIR/libXray" pull --ff-only
fi

echo "==> Building Android AAR..."
bash "$SCRIPT_DIR/build-android.sh"

echo "==> Building iOS XCFramework..."
bash "$SCRIPT_DIR/build-ios.sh"

echo "==> Copying artifacts..."
cp "$SCRIPT_DIR/output/android/XrayCore.aar" "$PROJECT_ROOT/androidApp/libs/XrayCore.aar"
cp -R "$SCRIPT_DIR/output/ios/LibXray.xcframework" "$PROJECT_ROOT/iosApp/Frameworks/LibXray.xcframework"

echo "==> Done. Artifacts placed in androidApp/libs/ and iosApp/Frameworks/"

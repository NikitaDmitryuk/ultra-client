#!/usr/bin/env bash
# Builds XrayCore.aar (Android) + LibXray.xcframework (iOS) and copies them into the project.
# Must be run on macOS (iOS build requires Xcode).
# Requires: Go 1.23+, Xcode 16+, Python 3.9+, Android NDK r27+, ANDROID_HOME, ANDROID_NDK_HOME
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "==> Building Android AAR..."
bash "$SCRIPT_DIR/build-android.sh"

echo "==> Building iOS XCFramework..."
bash "$SCRIPT_DIR/build-ios.sh"

echo "==> Copying artifacts into project..."
mkdir -p "$PROJECT_ROOT/androidApp/libs"
cp "$SCRIPT_DIR/output/android/XrayCore.aar" "$PROJECT_ROOT/androidApp/libs/XrayCore.aar"

mkdir -p "$PROJECT_ROOT/iosApp/Frameworks"
cp -R "$SCRIPT_DIR/output/ios/LibXray.xcframework" "$PROJECT_ROOT/iosApp/Frameworks/LibXray.xcframework"

echo "==> Done. Artifacts placed in androidApp/libs/ and iosApp/Frameworks/"

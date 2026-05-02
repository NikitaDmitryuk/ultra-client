#!/usr/bin/env bash
# Requires: macOS, Xcode 16+, Go 1.23+, Python 3.9+
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="$SCRIPT_DIR/xray-core-src"
OUTPUT_DIR="$SCRIPT_DIR/output/ios"

mkdir -p "$OUTPUT_DIR"

command -v xcodebuild >/dev/null 2>&1 || { echo "Xcode not found"; exit 1; }

echo "==> Installing gomobile..."
go install golang.org/x/mobile/cmd/gomobile@latest
go install golang.org/x/mobile/cmd/gobind@latest
export PATH="$PATH:$(go env GOPATH)/bin"
gomobile init

echo "==> Building iOS XCFramework via libXray build script..."
cd "$SRC_DIR/libXray"

if [ -f "build/main.py" ]; then
    python3 build/main.py apple gomobile
    cp -R output/apple/gomobile/LibXray.xcframework "$OUTPUT_DIR/LibXray.xcframework"
else
    echo "==> Fallback: direct gomobile build..."
    gomobile bind \
        -o "$OUTPUT_DIR/LibXray.xcframework" \
        -target "ios,iossimulator" \
        -iosversion 16.0 \
        -ldflags="-s -w" \
        -trimpath \
        github.com/xtls/libxray
fi

echo "==> iOS XCFramework built: $OUTPUT_DIR/LibXray.xcframework"

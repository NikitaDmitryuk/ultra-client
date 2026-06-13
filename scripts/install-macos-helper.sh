#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HELPER_SRC="$ROOT_DIR/desktop-helper/macos/UltraVpnHelper.swift"
BUILD_DIR="$ROOT_DIR/desktop-helper/macos/build"
HELPER_BIN="$BUILD_DIR/ultra-vpn-helper"
INSTALL_BIN="/Library/PrivilegedHelperTools/ultra-vpn-helper"
PLIST="/Library/LaunchDaemons/io.nikdmitryuk.ultraclient.helper.plist"
LABEL="io.nikdmitryuk.ultraclient.helper"
ALLOWED_UID="${ULTRA_ALLOWED_UID:-$(id -u)}"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "macOS helper can only be installed on Darwin" >&2
  exit 1
fi

mkdir -p "$BUILD_DIR"
swiftc "$HELPER_SRC" -o "$HELPER_BIN"

sudo install -m 755 -o root -g wheel "$HELPER_BIN" "$INSTALL_BIN"
sudo tee "$PLIST" >/dev/null <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>$LABEL</string>
    <key>ProgramArguments</key>
    <array>
        <string>$INSTALL_BIN</string>
    </array>
    <key>EnvironmentVariables</key>
    <dict>
        <key>ULTRA_ALLOWED_UID</key>
        <string>$ALLOWED_UID</string>
    </dict>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
    <key>StandardOutPath</key>
    <string>/var/log/ultra-client-helper.log</string>
    <key>StandardErrorPath</key>
    <string>/var/log/ultra-client-helper.err.log</string>
</dict>
</plist>
PLIST
sudo chown root:wheel "$PLIST"
sudo chmod 644 "$PLIST"
sudo launchctl bootout system "$PLIST" >/dev/null 2>&1 || true
sudo launchctl bootstrap system "$PLIST"
sudo launchctl enable "system/$LABEL"

echo "Installed $LABEL for uid $ALLOWED_UID"

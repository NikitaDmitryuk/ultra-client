#!/usr/bin/env bash
set -euo pipefail

PLIST="/Library/LaunchDaemons/io.nikdmitryuk.ultraclient.helper.plist"
BIN="/Library/PrivilegedHelperTools/ultra-vpn-helper"
SOCKET="/var/run/ultra-client-helper.sock"

sudo launchctl bootout system "$PLIST" >/dev/null 2>&1 || true
sudo rm -f "$PLIST" "$BIN" "$SOCKET"

echo "Uninstalled io.nikdmitryuk.ultraclient.helper"

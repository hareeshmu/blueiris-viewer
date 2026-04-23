#!/usr/bin/env bash
# Install + pre-seed blueiris-viewer on an Android device.
#
# Usage:
#   scripts/deploy.sh <adb-device-id> [stream-path]
# Examples:
#   scripts/deploy.sh 192.168.1.50:36623 G
#   scripts/deploy.sh 192.168.1.51:5555          # uses BI_DEFAULT_PATH from .env
#
# Requires: adb on PATH, APK at app/build/outputs/apk/debug/app-debug.apk,
#           .env file at repo root (copy from .env.example).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

ENV_FILE="$REPO_ROOT/.env"
APK="$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk"
PKG="com.hareesh.blueirisviewer"

if [[ ! -f "$ENV_FILE" ]]; then
    echo "Error: $ENV_FILE not found. Copy .env.example to .env and fill in values." >&2
    exit 1
fi

# shellcheck disable=SC1090
source "$ENV_FILE"

: "${BI_USER:?BI_USER not set in .env}"
: "${BI_PASS:?BI_PASS not set in .env}"
: "${BI_HOST:?BI_HOST not set in .env}"
: "${BI_PORT:=81}"
: "${RECONNECT_SECONDS:=3}"
: "${AUTOSTART:=true}"

DEVICE="${1:?Usage: $0 <adb-device-id> [stream-path]}"
STREAM_PATH="${2:-${BI_DEFAULT_PATH:-G}}"

if [[ ! -f "$APK" ]]; then
    echo "APK not found at $APK. Run ./gradlew assembleDebug first." >&2
    exit 1
fi

URL="rtsp://${BI_USER}:${BI_PASS}@${BI_HOST}:${BI_PORT}/${STREAM_PATH}"

echo "→ Target device: $DEVICE"
echo "→ Stream URL:    rtsp://${BI_USER}:***@${BI_HOST}:${BI_PORT}/${STREAM_PATH}"
echo "→ Installing APK..."
adb -s "$DEVICE" install -r "$APK"

PREFS="<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\" ?>
<map>
    <string name=\"url\">${URL}</string>
    <boolean name=\"transport_tcp\" value=\"true\" />
    <int name=\"reconnect_seconds\" value=\"${RECONNECT_SECONDS}\" />
    <boolean name=\"autostart\" value=\"${AUTOSTART}\" />
</map>"

echo "→ Pushing prefs..."
adb -s "$DEVICE" shell "am force-stop $PKG"
adb -s "$DEVICE" shell "rm -f /data/local/tmp/blueiris-viewer.xml"
echo "$PREFS" | adb -s "$DEVICE" shell "cat > /data/local/tmp/blueiris-viewer.xml"
adb -s "$DEVICE" shell "run-as $PKG sh -c 'mkdir -p shared_prefs && cp /data/local/tmp/blueiris-viewer.xml shared_prefs/blueiris-viewer.xml'"
adb -s "$DEVICE" shell "rm /data/local/tmp/blueiris-viewer.xml"

echo "→ Launching..."
adb -s "$DEVICE" shell "am start -n $PKG/.PlayerActivity"

echo "Done. Check the device screen. Watch RTSP handshake:"
echo "  adb -s $DEVICE logcat | grep -E 'RtspClient|BlueIrisViewer'"

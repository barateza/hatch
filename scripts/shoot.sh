#!/usr/bin/env bash
# Debug-build screenshot helper (state extras are ignored by release builds).
# Usage: scripts/shoot.sh <variant> <state> <quality> <outfile.png> [extra am args...]
set -euo pipefail
ADB="${ADB:-$HOME/Android/Sdk/platform-tools/adb}"
SERIAL="${SERIAL:-emulator-5554}"
VARIANT="${1:-AURORA}"
STATE="${2:-IDLE}"
QUALITY="${3:-GOOD}"
OUT="${4:-captures/${VARIANT}-${STATE}.png}"
shift 4 || true
APP=pw.barateza.hatch
"$ADB" -s "$SERIAL" shell am force-stop "$APP" >/dev/null 2>&1 || true
"$ADB" -s "$SERIAL" shell am start -W -n "$APP/.MainActivity" \
  --es variant "$VARIANT" --es state "$STATE" --es quality "$QUALITY" "$@" >/dev/null
sleep 2.2
mkdir -p "$(dirname "$OUT")"
"$ADB" -s "$SERIAL" exec-out screencap -p > "$OUT"
echo "$OUT $(stat -c%s "$OUT") bytes"

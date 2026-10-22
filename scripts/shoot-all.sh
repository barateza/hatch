#!/usr/bin/env bash
# Capture the three directions in key states.
set -euo pipefail
cd "$(dirname "$0")/.."
for v in AURORA GRID TACTILE; do
  scripts/shoot.sh "$v" IDLE GOOD "captures/${v,,}-idle.png"
  scripts/shoot.sh "$v" CONNECTED GOOD "captures/${v,,}-connected.png"
  scripts/shoot.sh "$v" RETRYING POOR "captures/${v,,}-retrying.png"
  scripts/shoot.sh "$v" NEEDS_AUTH GOOD "captures/${v,,}-auth.png"
  scripts/shoot.sh "$v" ERROR GOOD "captures/${v,,}-error.png"
done

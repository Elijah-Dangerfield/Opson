#!/bin/zsh
# Installs a built Opson.app on a booted simulator, launches it, waits, and
# fails if the app is no longer running or a new crash report appeared.
# Unit tests never launch the app; this catches what only a launch shows
# (a crash in something every launch runs, a bad Room migration).
#
#   scripts/ios_launch_smoke.sh <path/to/Opson.app> [simulator-udid] [seconds]
set -euo pipefail

app=${1:?path to the built Opson.app}
udid=${2:-booted}
wait_s=${3:-15}
bundle=com.opson.Opson
reports=~/Library/Logs/DiagnosticReports

before=$(ls "$reports" 2>/dev/null | grep -c '^Opson-' || true)
xcrun simctl install "$udid" "$app"
xcrun simctl terminate "$udid" "$bundle" >/dev/null 2>&1 || true
xcrun simctl launch "$udid" "$bundle" >/dev/null
sleep "$wait_s"

after=$(ls "$reports" 2>/dev/null | grep -c '^Opson-' || true)
if (( after > before )); then
  echo "FAIL: new crash report: $(ls -t "$reports" | grep '^Opson-' | head -1)"
  exit 1
fi
running=$(xcrun simctl spawn "$udid" launchctl list)
if [[ "$running" != *"UIKitApplication:$bundle"* ]]; then
  echo "FAIL: $bundle is not running after ${wait_s}s"
  exit 1
fi
echo "OK: $bundle launched and stayed up for ${wait_s}s"

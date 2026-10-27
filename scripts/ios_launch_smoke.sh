#!/bin/zsh
# Installs a built Opson.app on a booted simulator, launches it, waits, and
# fails if the app is no longer running or a new crash report appeared.
# Unit tests never launch the app; this catches what only a launch shows
# (a crash in something every launch runs, a bad Room migration).
#
# It also fails when the app's Kotlin code was built from another commit than
# this checkout's HEAD. A failed Kotlin compile used to leave Xcode linking the
# previous framework next to new resources, and that app launched fine until
# the first string whose offset had moved.
#
#   scripts/ios_launch_smoke.sh <path/to/Opson.app> [simulator-udid] [seconds]
set -euo pipefail

app=${1:?path to the built Opson.app}
udid=${2:-booted}
wait_s=${3:-15}
bundle=com.opson.Opson
reports=~/Library/Logs/DiagnosticReports

# The build stamps the commit into the Kotlin code (BuildInfo.commitSha), and
# Kotlin/Native keeps string literals as UTF-16. Debug builds put that code in
# <exe>.debug.dylib, release builds in the executable itself.
head=$(git -C "${0:A:h}/.." rev-parse --short=12 HEAD)
exe=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleExecutable' "$app/Info.plist")
if ! python3 -I -c 'import sys; sha = sys.argv[1].encode("utf-16-le"); sys.exit(0 if any(sha in open(p, "rb").read() for p in sys.argv[2:]) else 1)' \
    "$head" "$app/$exe" ${app}/${exe}.debug.dylib(N); then
  echo "FAIL: $app holds no Kotlin code built from $head. The Kotlin framework is stale: rebuild it."
  exit 1
fi

crash_reports() { ls "$reports" 2>/dev/null | grep '^Opson-' || true; }
before=$(crash_reports)
xcrun simctl install "$udid" "$app"
xcrun simctl terminate "$udid" "$bundle" >/dev/null 2>&1 || true
xcrun simctl launch "$udid" "$bundle" >/dev/null
sleep "$wait_s"

# Compared by name, not by count: the system prunes old reports, so a count can
# stay level while a new one arrives.
new=$(comm -13 <(print -r -- "$before" | sort) <(crash_reports | sort))
if [[ -n "$new" ]]; then
  echo "FAIL: new crash report: $(print -r -- "$new" | head -1)"
  exit 1
fi
running=$(xcrun simctl spawn "$udid" launchctl list)
if [[ "$running" != *"UIKitApplication:$bundle"* ]]; then
  echo "FAIL: $bundle is not running after ${wait_s}s"
  exit 1
fi
echo "OK: $bundle launched and stayed up for ${wait_s}s"

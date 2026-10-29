#!/usr/bin/env bash
# Decodes the widget snapshot fixtures (apps/ios/TestFixtures/widgets, written by
# :libraries:widgets' WidgetSnapshotFixtureTest) with the iOS extension's own
# WidgetSnapshot.swift, compiled for this Mac. Fails when the Kotlin writer and
# the Swift reader stop agreeing, which on a phone looks like a widget stuck on
# "Open Opson" or a 0.
#
#   scripts/check_widget_snapshot.sh
#
# After changing the snapshot on purpose, record the fixtures first:
#   RECORD_WIDGET_FIXTURES=1 ./gradlew :libraries:widgets:testDebugUnitTest
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
fixtures="$root/apps/ios/TestFixtures/widgets"
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT

xcrun swiftc -O \
  -o "$out/check_widget_snapshot" \
  "$root/apps/ios/OpsonWidgets/WidgetSnapshot.swift" \
  "$fixtures/check/main.swift"

"$out/check_widget_snapshot" "$fixtures"

#!/bin/sh
# Deletes build output and IDE state. Leaves alone everything git tracks: the
# Xcode project's shared workspace holds Package.resolved.
set -e
cd "$(dirname "$0")/.."
./gradlew clean
rm -rf .idea .gradle build
rm -rf apps/ios/iosApp.xcodeproj/xcuserdata

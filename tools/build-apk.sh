#!/usr/bin/env bash
# Bumps versionCode, builds the debug APK and copies it to dist/hub-<versionName>.apk.
set -euo pipefail
cd "$(dirname "$0")/.."

props=version.properties
code=$(grep -E '^versionCode=' "$props" | cut -d= -f2)
marketing=$(grep -E '^marketingVersion=' "$props" | cut -d= -f2)
next=$((code + 1))
sed -i '' -e "s/^versionCode=.*/versionCode=$next/" "$props"
name="$marketing.$next"
echo "Building Hub $name (versionCode $next)"

./gradlew :app:assembleDebug --console=plain -q

out="dist/hub-$name.apk"
cp app/build/outputs/apk/debug/app-debug.apk "$out"
echo "APK: $out"

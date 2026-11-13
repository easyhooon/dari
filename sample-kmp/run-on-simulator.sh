#!/usr/bin/env bash
# Builds sample-kmp and runs sample-ios with it on the booted iOS Simulator.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
framework="$root/sample-kmp/build/bin/iosSimulatorArm64/debugFramework"
derived="$root/build/sample-kmp-ios"
udid="$(xcrun simctl list devices booted | grep -Eo '[0-9A-F-]{36}' | head -1)"
[ -n "$udid" ] || { echo "Boot an iOS Simulator first." >&2; exit 1; }

"$root/gradlew" -p "$root" :sample-kmp:linkDebugFrameworkIosSimulatorArm64
xcodebuild -project "$root/sample-ios/DariSample.xcodeproj" -scheme DariSample -configuration Debug \
  -destination "platform=iOS Simulator,id=$udid" -derivedDataPath "$derived" \
  "FRAMEWORK_SEARCH_PATHS=\$(inherited) $framework" "OTHER_LDFLAGS=\$(inherited) -framework DariKmpSample" build

app="$derived/Build/Products/Debug-iphonesimulator/DariSample.app"
xcrun simctl install "$udid" "$app"
xcrun simctl launch "$udid" "$(/usr/libexec/PlistBuddy -c 'Print CFBundleIdentifier' "$app/Info.plist")"

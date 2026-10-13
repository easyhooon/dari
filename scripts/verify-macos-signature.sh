#!/usr/bin/env bash
# Verifies the Developer ID chain, team, hardened runtime and JVM entitlements.
set -euo pipefail

app="$1"
team="$2"
entitlements="$(cd "$(dirname "$0")/.." && pwd)/dari-desktop-app/packaging/macos-entitlements.plist"
[[ "$team" =~ ^[A-Z0-9]{10}$ ]] || { echo "Invalid Apple Team ID." >&2; exit 1; }
requirement="anchor apple generic and certificate 1[field.1.2.840.113635.100.6.2.6] exists and certificate leaf[field.1.2.840.113635.100.6.1.13] exists and certificate leaf[subject.OU] = \"$team\""
codesign --verify --deep --strict --all-architectures --test-requirement "=$requirement" "$app"
for target in "$app" "$app/Contents/runtime" "$app/Contents/runtime/Contents/Home/lib/server/libjvm.dylib"; do
  details="$(codesign -d --verbose=4 "$target" 2>&1)"
  grep -q 'flags=.*runtime' <<< "$details"
  grep -q '^Timestamp=' <<< "$details"
  grep -q "^TeamIdentifier=$team$" <<< "$details"
done
# The JVM runs inside the launcher process, so the app executable carries the JIT entitlements.
codesign -d --entitlements - --xml "$app" 2>/dev/null | python3 -c '
import plistlib, sys
actual = plistlib.loads(sys.stdin.buffer.read())
with open(sys.argv[1], "rb") as expected_file:
    expected = plistlib.load(expected_file)
for key, value in expected.items():
    if actual.get(key) != value:
        sys.exit("Missing JVM entitlement: " + key)
' "$entitlements"

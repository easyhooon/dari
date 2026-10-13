#!/usr/bin/env bash
# Packages the macOS desktop app for a release, mirroring Necto's approach: an ad-hoc signed
# Dari.app (no Developer ID, no notarization) in a DMG, plus a SHA-256 file.
#   scripts/package-desktop-app.sh            # version from gradle/libs.versions.toml
# Output: dari-desktop-app/build/release/Dari-<version>-<arch>.dmg(.sha256)
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

version="$(sed -n 's/^dari = "\(.*\)"/\1/p' gradle/libs.versions.toml)"
arch="$(uname -m)"
[ "$arch" = "x86_64" ] && arch="x64"

./gradlew -PskipPublicationSigning :dari-desktop-app:createDistributable

app="dari-desktop-app/build/compose/binaries/main/app/Dari.app"
# jpackage already signs ad-hoc with the JVM's entitlements; re-signing would drop them, so only verify.
codesign --verify --deep --strict "$app"
codesign -dv "$app" 2>&1 | grep -q "flags=.*adhoc"

out="dari-desktop-app/build/release"
dmg="$out/Dari-$version-$arch.dmg"
stage="$(mktemp -d)"
trap 'rm -rf "$stage"' EXIT
mkdir -p "$out"
cp -R "$app" "$stage/"
ln -s /Applications "$stage/Applications"
hdiutil create -volname "Dari $version" -srcfolder "$stage" -ov -format UDZO -quiet "$dmg"
(cd "$out" && shasum -a 256 "$(basename "$dmg")" > "$(basename "$dmg").sha256")

echo "$dmg"

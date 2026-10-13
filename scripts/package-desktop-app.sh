#!/usr/bin/env bash
# Packages the desktop app for the OS it runs on, plus a SHA-256 file for each artifact.
#   macOS:   ad-hoc signed Dari.app in Dari-<version>-<arm64|x64>.dmg (no Developer ID, no notarization)
#   Linux:   dari_<version>_<amd64|arm64>.deb
#   Windows: Dari-<version>-x64.msi (unsigned; run from Git Bash)
# Output: dari-desktop-app/build/release/
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

version="$(sed -n 's/^dari = "\(.*\)"/\1/p' gradle/libs.versions.toml)"
binaries="dari-desktop-app/build/compose/binaries/main"
out="dari-desktop-app/build/release"
mkdir -p "$out"

checksum() {
  (cd "$out" && { command -v sha256sum > /dev/null && sha256sum "$1" || shasum -a 256 "$1"; } > "$1.sha256")
  echo "$out/$1"
}

case "$(uname -s)" in
  Darwin)
    arch="$(uname -m)"
    [ "$arch" = "x86_64" ] && arch="x64"
    ./gradlew -PskipPublicationSigning :dari-desktop-app:createDistributable
    app="$binaries/app/Dari.app"
    # On Apple Silicon jpackage already signs ad-hoc with the JVM's entitlements; re-signing would drop
    # them. On Intel it leaves the app unsigned, so sign it ad-hoc here.
    if ! codesign --verify --deep --strict "$app" 2> /dev/null; then
      codesign --force --deep --sign - "$app"
    fi
    codesign --verify --deep --strict "$app"
    codesign -dv "$app" 2>&1 | grep -q "flags=.*adhoc"
    name="Dari-$version-$arch.dmg"
    stage="$(mktemp -d)"
    trap 'rm -rf "$stage"' EXIT
    cp -R "$app" "$stage/"
    ln -s /Applications "$stage/Applications"
    hdiutil create -volname "Dari $version" -srcfolder "$stage" -ov -format UDZO -quiet "$out/$name"
    checksum "$name"
    ;;
  Linux)
    ./gradlew -PskipPublicationSigning :dari-desktop-app:packageDeb
    deb="$(find "$binaries/deb" -name '*.deb' | head -n 1)"
    arch="$(dpkg --print-architecture)"
    name="dari_${version}_$arch.deb"
    cp "$deb" "$out/$name"
    checksum "$name"
    ;;
  MINGW* | MSYS* | CYGWIN*)
    ./gradlew -PskipPublicationSigning :dari-desktop-app:packageMsi
    msi="$(find "$binaries/msi" -name '*.msi' | head -n 1)"
    name="Dari-$version-x64.msi"
    cp "$msi" "$out/$name"
    checksum "$name"
    ;;
  *)
    echo "Unsupported OS: $(uname -s)" >&2
    exit 1
    ;;
esac

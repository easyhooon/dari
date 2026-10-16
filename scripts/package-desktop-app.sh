#!/usr/bin/env bash
# Packages the desktop app for the OS it runs on, plus a SHA-256 file for each artifact.
#   macOS:   Developer ID signed, notarized, stapled Dari.app and arm64/x64 DMG
#   Linux:   dari_<version>_<amd64|arm64>.deb
#   Windows: Dari-<version>-x64.msi (unsigned; run from Git Bash)
# Output: dari-desktop-app/build/release/
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

version="$(sed -n 's/^dari = "\(.*\)"/\1/p' gradle/libs.versions.toml)"
binaries="dari-desktop-app/build/compose/binaries/main-release"
out="dari-desktop-app/build/release"
mkdir -p "$out"

checksum() {
  (cd "$out" && { command -v sha256sum > /dev/null && sha256sum "$1" || shasum -a 256 "$1"; } > "$1.sha256")
  echo "$out/$1"
}

case "$(uname -s)" in
  Darwin)
    for variable in MACOS_SIGNING_IDENTITY MACOS_SIGNING_KEYCHAIN MACOS_NOTARY_PROFILE APPLE_TEAM_ID; do
      [ -n "${!variable:-}" ] || { echo "Missing $variable for macOS release packaging." >&2; exit 1; }
    done
    [[ "$APPLE_TEAM_ID" =~ ^[A-Z0-9]{10}$ ]] || { echo "Invalid Apple Team ID." >&2; exit 1; }
    command -v "${DMGBUILD:-dmgbuild}" > /dev/null || { echo "dmgbuild is required: pip install dmgbuild" >&2; exit 1; }
    arch="$(uname -m)"
    [ "$arch" = "x86_64" ] && arch="x64"
    case "$arch" in arm64 | x64) ;; *) echo "Unsupported macOS architecture." >&2; exit 1 ;; esac
    name="Dari-$version-$arch.dmg"
    rm -f "$out/$name" "$out/$name.sha256"
    stage="$(mktemp -d)"
    trap 'rm -rf "$stage"' EXIT
    export DARI_MACOS_SIGN=true
    ./gradlew -PskipPublicationSigning :dari-desktop-app:createReleaseDistributable
    app="$binaries/app/Dari.app"
    [ "$(plutil -extract CFBundleShortVersionString raw -o - "$app/Contents/Info.plist")" = "$version" ] || {
      echo "The packaged app version does not match the release version." >&2; exit 1;
    }
    [ "$(plutil -extract CFBundleIdentifier raw -o - "$app/Contents/Info.plist")" = "io.github.easyhooon.dari.desktop" ] || {
      echo "The packaged app has an unexpected bundle identifier." >&2; exit 1;
    }
    scripts/verify-macos-signature.sh "$app" "$APPLE_TEAM_ID"
    # Exercise the signed JVM and native libraries without rebuilding the bundle.
    scripts/smoke-test-desktop-app.sh --skip-build

    notarize() {
      local notary_exit=0
      xcrun notarytool submit "$1" --keychain-profile "$MACOS_NOTARY_PROFILE" \
          --keychain "$MACOS_SIGNING_KEYCHAIN" --wait --timeout 30m --output-format json \
          > "$stage/notary-result.json" 2> "$stage/notary-error.log" || notary_exit=$?
      python3 - "$stage/notary-result.json" "$notary_exit" <<'PY'
import json, re, sys
try:
    with open(sys.argv[1]) as result:
        submission = json.load(result)
except (ValueError, OSError):
    sys.exit("Apple notarization submission failed; no valid result was returned.")
status = submission.get("status")
identifier = submission.get("id", "")
if isinstance(identifier, str) and re.fullmatch(r"[0-9a-fA-F-]{36}", identifier):
    print("Notarization submission ID: " + identifier)
if status in ("Accepted", "Invalid", "In Progress"):
    print("Notarization status: " + status)
if sys.argv[2] != "0" or status != "Accepted":
    sys.exit("Apple did not accept the submission. Use notarytool log with its ID to investigate.")
PY
    }

    # Stapling fetches the ticket from Apple's CDN, which drops connections now and then
    # (NSURLErrorDomain -1005, "Error 68"). It is idempotent, so retry with growing waits.
    staple() {
      local attempt
      for attempt in 1 2 3 4 5; do
        if xcrun stapler staple "$1" && xcrun stapler validate "$1"; then
          return 0
        fi
        if [ "$attempt" -lt 5 ]; then
          echo "Stapling $1 failed (attempt $attempt of 5); retrying." >&2
          sleep $((attempt * ${STAPLE_RETRY_DELAY:-15}))
        fi
      done
      echo "Stapling $1 failed after 5 attempts." >&2
      return 1
    }

    # Staple the app before placing it in the DMG, including for offline in-app updates.
    ditto -c -k --sequesterRsrc --keepParent "$app" "$stage/Dari.zip"
    notarize "$stage/Dari.zip"
    staple "$app"
    spctl --assess --type execute --verbose=2 "$app"
    dmg="$stage/$name"
    # dmgbuild lays out the window (background arrow, icon positions) without scripting Finder.
    "${DMGBUILD:-dmgbuild}" -s dari-desktop-app/packaging/dmg/dmg-settings.py -D app="$app" \
        -D background=dari-desktop-app/packaging/dmg/background.png "Dari $version" "$dmg"
    codesign --sign "$MACOS_SIGNING_IDENTITY" --keychain "$MACOS_SIGNING_KEYCHAIN" --timestamp "$dmg"
    codesign --verify --strict "$dmg"
    notarize "$dmg"
    staple "$dmg"
    spctl --assess --type open --context context:primary-signature --verbose=2 "$dmg"
    cp "$dmg" "$out/$name"
    # Stapling changes the DMG bytes; publish only the hash of the final disk image.
    checksum "$name"
    ;;
  Linux)
    ./gradlew -PskipPublicationSigning :dari-desktop-app:packageReleaseDeb
    deb="$(find "$binaries/deb" -name '*.deb' | head -n 1)"
    arch="$(dpkg --print-architecture)"
    name="dari_${version}_$arch.deb"
    cp "$deb" "$out/$name"
    checksum "$name"
    ;;
  MINGW* | MSYS* | CYGWIN*)
    ./gradlew -PskipPublicationSigning :dari-desktop-app:packageReleaseMsi
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

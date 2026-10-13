#!/usr/bin/env bash
# Installs the latest Dari desktop app on macOS.
#
#   curl -fsSL https://raw.githubusercontent.com/easyhooon/dari/main/scripts/install-desktop.sh | bash
#
# Picks the DMG for this Mac's chip, verifies it against the release's .sha256 file, and copies
# Dari.app into /Applications (replacing an older copy). curl adds no download quarantine flag,
# so Gatekeeper's first-launch prompt doesn't appear; the checksum is what catches a bad download.
# Environment: DARI_VERSION=2.0.0 pins a version; DARI_INSTALL_DIR changes the target folder.
set -euo pipefail

repo="easyhooon/dari"
install_dir="${DARI_INSTALL_DIR:-/Applications}"

if [ "$(uname -s)" != "Darwin" ]; then
  echo "This installer is for macOS. Other platforms: https://easyhooon.github.io/dari/download" >&2
  exit 1
fi

case "$(uname -m)" in
  arm64) arch="arm64" ;;
  x86_64) arch="x64" ;;
  *) echo "Unsupported CPU: $(uname -m)" >&2; exit 1 ;;
esac

# The /releases/latest redirect names the newest tag without calling the GitHub API.
version="${DARI_VERSION:-}"
if [ -z "$version" ]; then
  latest="$(curl -fsSLI -o /dev/null -w '%{url_effective}' "https://github.com/$repo/releases/latest")"
  version="${latest##*/}"
fi
[ -n "$version" ] || { echo "Couldn't find the latest Dari release." >&2; exit 1; }

dmg="Dari-$version-$arch.dmg"
base="https://github.com/$repo/releases/download/$version"
work="$(mktemp -d)"
mount_point=""
cleanup() {
  [ -n "$mount_point" ] && hdiutil detach "$mount_point" -quiet || true
  rm -rf "$work"
}
trap cleanup EXIT

echo "Downloading Dari $version for $arch…"
if ! curl -fL --progress-bar -o "$work/$dmg" "$base/$dmg" || ! curl -fsSL -o "$work/$dmg.sha256" "$base/$dmg.sha256"; then
  echo "No $dmg in release $version: https://github.com/$repo/releases" >&2
  exit 1
fi

expected="$(awk '{print $1}' "$work/$dmg.sha256")"
actual="$(shasum -a 256 "$work/$dmg" | awk '{print $1}')"
if [ "$expected" != "$actual" ]; then
  echo "Checksum mismatch for $dmg; not installing." >&2
  exit 1
fi
echo "Checksum OK."

mount_point="$(hdiutil attach -nobrowse -readonly "$work/$dmg" 2> /dev/null | awk -F'\t' '/\/Volumes\//{print $NF}')"
mkdir -p "$install_dir"
if [ -d "$install_dir/Dari.app" ]; then
  echo "Replacing $install_dir/Dari.app"
  rm -rf "$install_dir/Dari.app"
fi
ditto "$mount_point/Dari.app" "$install_dir/Dari.app"

echo "Installed Dari $version to $install_dir/Dari.app"
echo "Open it with: open \"$install_dir/Dari.app\""

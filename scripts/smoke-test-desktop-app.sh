#!/usr/bin/env bash
# Launches the packaged desktop app (from createDistributable) and fails if it exits within
# a few seconds. Catches startup crashes on each OS; it does not check what is drawn.
# On a headless Linux runner, wrap it: xvfb-run scripts/smoke-test-desktop-app.sh
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

if [ "${1:-}" != "--skip-build" ]; then
  ./gradlew -PskipPublicationSigning :dari-desktop-app:createDistributable
fi
app_dir="dari-desktop-app/build/compose/binaries/main/app"
case "$(uname -s)" in
  Darwin) exe="$app_dir/Dari.app/Contents/MacOS/Dari" ;;
  Linux) exe="$(find "$app_dir" -maxdepth 3 -path '*/bin/*' -type f -perm -u+x | head -n 1)" ;;
  *) exe="$(find "$app_dir" -maxdepth 2 -name 'Dari.exe' | head -n 1)" ;;
esac
[ -n "$exe" ] && [ -e "$exe" ] || { echo "App executable not found under $app_dir" >&2; exit 1; }

log="$(mktemp)"
"$exe" > "$log" 2>&1 &
pid=$!
sleep "${SMOKE_SECONDS:-15}"
if ! kill -0 "$pid" 2> /dev/null; then
  echo "Dari exited during startup:" >&2
  cat "$log" >&2
  exit 1
fi
kill "$pid" 2> /dev/null || true
if grep -qiE "exception|fatal" "$log"; then
  echo "Dari logged an error during startup:" >&2
  cat "$log" >&2
  exit 1
fi
echo "Dari stayed up for ${SMOKE_SECONDS:-15}s."

#!/usr/bin/env bash
# Fails if the public `Dari` API differs between dari, dari-desktop and dari-noop,
# so swapping the dependency never breaks app code. Run via `./gradlew checkDariApiParity`.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
classes="build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"

public_api() {
  javap -public -cp "$root/$1/$classes" com.easyhooon.dari.Dari \
    | grep -E '^  public' \
    | grep -vE '\$[a-z_-]+\(|access\$|\$stable' \
    | sed -E 's/ synchronized / /' \
    | sort
}

expected="$(public_api dari)"
status=0
for module in dari-desktop dari-noop; do
  if ! diff <(echo "$expected") <(public_api "$module") > /dev/null; then
    echo "Dari API in :$module differs from :dari (< dari, > $module):"
    diff <(echo "$expected") <(public_api "$module") || true
    status=1
  fi
done
[ "$status" -eq 0 ] && echo "Dari API matches across dari, dari-desktop and dari-noop."
exit "$status"

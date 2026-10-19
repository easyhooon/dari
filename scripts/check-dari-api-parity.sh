#!/usr/bin/env bash
# Fails if the public `Dari` API, or the `DariHooks` the Gradle plugin calls, differs between dari and dari-noop,
# so swapping the dependency never breaks app code or instrumented builds. Run via `./gradlew checkDariApiParity`.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
classes="build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"

public_api() {
  javap -public -cp "$root/$1/$classes" "$2" \
    | grep -E '^  public' \
    | grep -vE '\$[a-z_-]+\(|access\$|\$stable' \
    | sed -E 's/ synchronized / /' \
    | sort
}

status=0
for class in com.easyhooon.dari.Dari com.easyhooon.dari.auto.DariHooks; do
  expected="$(public_api dari "$class")"
  for module in dari-noop; do
    if ! diff <(echo "$expected") <(public_api "$module" "$class") > /dev/null; then
      echo "$class in :$module differs from :dari (< dari, > $module):"
      diff <(echo "$expected") <(public_api "$module" "$class") || true
      status=1
    fi
  done
done
[ "$status" -eq 0 ] && echo "Dari API matches across dari and dari-noop."
exit "$status"

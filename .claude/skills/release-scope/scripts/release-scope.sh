#!/usr/bin/env bash
# Classifies changes since the last release and prints which version(s) the next release must bump.
# Usage: release-scope.sh [base_tag] [ref]   (defaults: latest x.y.z tag, origin/main)
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
git fetch -q origin --tags 2> /dev/null || echo "warning: git fetch failed; using local refs" >&2

base="${1:-$(git tag --sort=-v:refname | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' | head -n 1)}"
ref="${2:-origin/main}"
[ -n "$base" ] || { echo "No x.y.z release tag found" >&2; exit 2; }
git rev-parse -q --verify "$base^{commit}" > /dev/null || { echo "Unknown base: $base" >&2; exit 2; }
git rev-parse -q --verify "$ref^{commit}" > /dev/null || { echo "Unknown ref: $ref" >&2; exit 2; }

# Order matters: the first matching rule wins.
classify() {
  case "$1" in
    */src/test/* | */src/*Test/* | */src/androidTest/*) echo test ;;
    dari/* | dari-core/* | dari-noop/* | dari-runtime/*) echo library ;;
    # The plugin ships to Maven Central with the library, under the same `dari` version.
    dari-gradle-plugin/src/main/* | dari-gradle-plugin/build.gradle.kts | dari-gradle-plugin/settings.gradle.kts) echo library ;;
    dari-inspector-protocol/*) echo protocol ;;
    dari-desktop-app/*) echo desktop ;;
    dari-web/*) echo web ;;
    fixtures/*) echo test ;;
    scripts/package-desktop-app.sh | scripts/smoke-test-desktop-app.sh | scripts/verify-macos-signature.sh \
      | scripts/test-macos-packaging.py | .github/workflows/desktop-*.yml) echo desktop-tooling ;;
    gradle/libs.versions.toml) echo version-catalog ;;
    build.gradle.kts | settings.gradle.kts | gradle.properties | gradle/* | detekt-config.yml | stability_config.conf) echo shared-build ;;
    *) echo no-release ;;
  esac
}

# One file per area; macOS ships bash 3.2, which has no associative arrays.
areas_dir="$(mktemp -d)"
trap 'rm -rf "$areas_dir"' EXIT
files() { cat "$areas_dir/$1" 2> /dev/null || true; }

git diff --name-only "$base" "$ref" | while IFS= read -r path; do
  [ -n "$path" ] && echo "$path" >> "$areas_dir/$(classify "$path")"
done

# Version-only and comment edits to the catalog (the release bump itself) don't count as a change.
catalog_other="$(git diff -U0 "$base" "$ref" -- gradle/libs.versions.toml \
  | grep -E '^[+-][^+-]' | grep -vE '^[+-][[:space:]]*#' | grep -vE '^[+-](dari|dariDesktop) *= *"' || true)"
if [ -n "$catalog_other" ]; then echo "gradle/libs.versions.toml (dependency versions)" >> "$areas_dir/shared-build"; fi

protocol_version_change="$(git diff "$base" "$ref" -- dari-inspector-protocol \
  | grep -E '^[+-].*const val VERSION *=' || true)"

version_of() {
  git show "$ref:gradle/libs.versions.toml" | sed -n "s/^$1 *= *\"\\(.*\\)\"/\\1/p"
}
dari_version="$(version_of dari)"
desktop_version="$(version_of dariDesktop)"

echo "Range: $base..$ref ($(git rev-list --count --no-merges "$base..$ref") commits)"
echo "On $ref: dari=$dari_version dariDesktop=${desktop_version:-<not split yet, follows dari>}"
echo

for area in library protocol desktop web desktop-tooling shared-build test no-release; do
  list="$(files "$area")"
  [ -n "$list" ] || continue
  count="$(printf '%s\n' "$list" | grep -c .)"
  echo "## $area ($count files)"
  printf '%s\n' "$list" | sed -n '1,8s/^/  /p'
  [ "$count" -le 8 ] || echo "  … $((count - 8)) more"
done
echo

echo "## Commits (no merges)"
# -n instead of `| head`: head exiting early kills git log with SIGPIPE, which pipefail turns into an exit.
git log --no-merges -n 40 --format='  %h %s' "$base..$ref"
echo

if [ -n "$(files library)" ] || [ -n "$(files protocol)" ]; then
  verdict=full
  reason="library or protocol code changed; apps only get it from Maven Central"
elif [ -n "$(files desktop)" ]; then
  verdict=desktop
  reason="only the desktop app changed; no library change to publish"
elif [ -n "$(files shared-build)" ]; then
  verdict=review
  reason="shared build files changed; check whether they alter the published library or the desktop app"
else
  verdict=none
  reason="docs, sample, tests, CI or packaging tooling only; nothing users install changes"
fi

echo "VERDICT: $verdict"
echo "Reason: $reason"
if [ "$verdict" != review ] && [ -n "$(files shared-build)" ]; then
  echo "Check: shared build files changed too; confirm they don't change the published library."
fi
if [ -n "$protocol_version_change" ]; then
  echo "WARNING: InspectorProtocol.VERSION changed:"
  printf '%s\n' "$protocol_version_change" | sed 's/^/  /'
  echo "  Apps and the desktop app must both update; say so under Behavior changes."
fi
# dari-web has its own version and tags (dari-web-vX.Y.Z), so it never changes the verdict above.
web_base="$(git tag --list 'dari-web-v*' --sort=-v:refname | head -n 1)"
web_from="${web_base:-$(git rev-list --max-parents=0 "$ref" | tail -n 1)}"
web_changes="$(git diff --name-only "$web_from" "$ref" -- dari-web | grep -vE '^dari-web/test/' | grep -c . || true)"
web_version="$(git show "$ref:dari-web/package.json" 2> /dev/null | sed -n 's/^  "version": "\(.*\)",/\1/p')"
if [ -n "$web_version" ]; then
  echo "Web: dari-web=$web_version, last tag ${web_base:-<never published>}, $web_changes non-test files changed since."
  [ "$web_changes" -eq 0 ] || echo "  Publish separately with /release web <version>."
fi
# No grep -q for the same reason: it stops reading at the first match.
if git log --no-merges --format='%s' "$base..$ref" | grep -iE '^feat(\(|:|!)' > /dev/null; then
  echo "Note: feat commits present; consider a minor bump instead of a patch."
fi

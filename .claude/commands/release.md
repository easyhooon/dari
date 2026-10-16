You are releasing a new version of dari. Arguments: $ARGUMENTS
- `<version>` (e.g. `2.3.0`): a full release. The library goes to Maven Central and the desktop app ships with the same number.
- `desktop <version>` (e.g. `desktop 2.2.2`): a desktop-only release. Nothing goes to Maven Central.
- No version: read it from `gradle/libs.versions.toml`, using `dari` for a full release and `dariDesktop` for `desktop`.

Both releases share one number line, set in `gradle/libs.versions.toml`: `dari` is the library, `dariDesktop` is the desktop app. They differ only after a desktop-only release.
The desktop updater, `install-desktop.sh` and the download page all read GitHub's `/releases/latest`, so every GitHub release needs the desktop installers, a plain semver tag, and must not be a draft or prerelease. Apps keep working on any library version from 2.0.0 because compatibility is decided by `InspectorProtocol.VERSION`, not by these numbers.

## Step 1: Pick the mode and verify the version
- Run `gh release list --limit 3` and note the latest tag. The new version must be higher, or installed desktop apps never see it.
- **Full release**: set both `dari` and `dariDesktop` to the version.
- **Desktop-only release**: set only `dariDesktop`; leave `dari` alone.
  - First run `git diff --stat <latest_tag>..HEAD -- dari dari-core dari-noop dari-runtime dari-inspector-protocol`. If it lists anything, stop and ask the user: library or protocol changes need a full release, or apps never get them.
- The bump must be on `main` before tagging, because `desktop-release.yml` checks out the tag and fails when `dariDesktop` doesn't match it.

## Step 2: Publish to Maven Central (full release only)
- Skip this step for a desktop-only release; publishing would put an unchanged library on Maven Central under a new number.
- Run `./gradlew clean publishAllPublicationsToMavenCentralRepository`
- Verify the build succeeds.

## Step 3: Create GitHub Release
- Run `git log --oneline <latest_tag>..HEAD` to get the commits since last release.
- Run `gh release view <latest_tag>` to reference the existing release note style.
- For each PR merged since the previous tag (the `Merge pull request #N` commits), run `gh pr view <N> --json body` and look for a **Behavior change** note. Commit messages don't carry these, so skipping this step drops changed defaults from the notes.
- Create a release note following the same format:
  - Title: version number (e.g., "1.3.1"). Desktop-only: `<version> (Desktop only)`
  - Tag: version number, with no prefix
  - Body structure:
    ```
    ## Behavior changes
    * <what changed for existing users, and how to get the old behavior> (<PR link>)

    ## What's Changed
    * <PR/feature title> by @easyhooon in <PR link>
      * <bullet points describing key changes>

    **Full Changelog**: https://github.com/easyhooon/dari/compare/<prev_tag>...<new_tag>
    ```
  - Desktop-only: start the body with `Desktop app only. The library stays at <dari>, so there is no <version> on Maven Central and apps don't need to update.`
- Omit `## Behavior changes` when no PR has one.
- Run `gh release create <version> --target main --title "<title>" --notes "<release notes>"`

## Step 4: Attach the desktop installers
- Run `gh workflow run desktop-release.yml -f tag=<version>` and wait for it with `gh run watch`.
- Run `gh release view <version> --json assets` and confirm the `.dmg`, `.exe`, `.msi` and `.sha256` files are there. Until they are, `/releases/latest` points installed apps at a release they can't download.

## Step 5: Report
- Print the release URL and confirm completion.

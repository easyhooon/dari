You are releasing a new version of dari. Arguments: $ARGUMENTS
- `<version>` (e.g. `2.3.0`): a full release. The library and the Gradle plugin go to Maven Central and the desktop app ships with the same number.
- `desktop <version>` (e.g. `desktop 2.2.2`): a desktop-only release. Nothing goes to Maven Central.
- `web <version>` (e.g. `web 0.1.1`): an npm release of `dari-web` only. See "Web release" below; skip Steps 1–5.
- No version: read it from `gradle/libs.versions.toml`, using `dari` for a full release and `dariDesktop` for `desktop`, or from `dari-web/package.json` for `web`.

Both releases share one number line, set in `gradle/libs.versions.toml`: `dari` is the library, `dariDesktop` is the desktop app. They differ only after a desktop-only release.
The desktop updater, `install-desktop.sh` and the download page all read GitHub's `/releases/latest`, so every GitHub release needs the desktop installers, a plain semver tag, and must not be a draft or prerelease. Apps keep working on any library version from 2.0.0 because compatibility is decided by `InspectorProtocol.VERSION`, not by these numbers.

## Step 1: Pick the mode and verify the version
- Everything going into the release must already be merged to `main` with CI green. If a feature PR is still open, verify it (and wait for its checks) and merge it first.
- Run `gh release list --limit 3` and note the latest tag. The new version must be higher, or installed desktop apps never see it.
- **Full release**: set both `dari` and `dariDesktop` to the version.
- **Desktop-only release**: set only `dariDesktop`; leave `dari` alone.
  - First run `git diff --stat <latest_tag>..HEAD -- dari dari-core dari-noop dari-runtime dari-inspector-protocol dari-gradle-plugin`. If it lists anything, stop and ask the user: library or protocol changes need a full release, or apps never get them.
- Make the bump in a PR: branch `chore/bump-desktop-<version>` (or `chore/bump-version-<version>` for a full release) from `origin/main`, commit `chore: bump the desktop app to <version>`, open the PR, wait for CI, then merge it.
- The bump must be on `main` before tagging, because `desktop-release.yml` checks out the tag and fails when `dariDesktop` doesn't match it.

## Step 2: Publish to Maven Central (full release only)
- Skip this step for a desktop-only release; publishing would put an unchanged library and plugin on Maven Central under a new number.
- Run `./gradlew clean publishAllPublicationsToMavenCentralRepository` for the library modules.
- Then publish the Gradle plugin in its own run: `./gradlew -p dari-gradle-plugin clean publishAllPublicationsToMavenCentralRepository`
  - Keep the runs separate. `:sample` applies the plugin, so a root run builds the plugin jar while configuring, and a root `clean` would delete it before upload. In 2.6.0 that left Central with "Bundle has no files".
  - It uploads the plugin jar and its marker, versioned by `dari`.
- Verify both runs succeed.
- Verify the plugin reached Central, because a missing marker breaks `plugins { id("io.github.easyhooon.dari") version "<version>" }`. Each URL must return 200. Central can take up to ~30 minutes, so retry before calling it failed:
  - `curl -sI https://repo1.maven.org/maven2/io/github/easyhooon/dari-gradle-plugin/<version>/dari-gradle-plugin-<version>.pom`
  - `curl -sI https://repo1.maven.org/maven2/io/github/easyhooon/dari/io.github.easyhooon.dari.gradle.plugin/<version>/io.github.easyhooon.dari.gradle.plugin-<version>.pom`

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
- Push the tag first; a draft release doesn't create it, and the workflow checks it out: `git fetch origin && git tag <version> origin/main && git push origin <version>`
- Create the release as a **draft**, so `/releases/latest` doesn't point installed apps at a release with no installers yet: `gh release create <version> --draft --verify-tag --title "<title>" --notes "<release notes>"`

## Step 4: Attach the desktop installers, then publish
- Run `gh workflow run desktop-release.yml -f tag=<version>` and wait for it with `gh run watch`.
- Run `gh release view <version> --json assets` and confirm every installer and its `.sha256` is there: `Dari-<version>-arm64.dmg`, `Dari-<version>-x64.dmg`, `Dari-<version>-x64.exe`, `Dari-<version>-x64.msi`, `dari_<version>_amd64.deb`, `Dari-<version>-linux-x64.tar.gz`.
- If the workflow fails or a file is missing, stop and leave the release as a draft.
- Publish: `gh release edit <version> --draft=false --latest`

## Step 5: Report
- Print the release URL and confirm completion.

## Web release (`web` only)
`dari-web` (`@easyhooon/dari-web` on npm) has its own semver in `dari-web/package.json`, unrelated to `dari` / `dariDesktop`.
- Find the last web tag: `git tag --list 'dari-web-v*' --sort=-v:refname | head -n 1`. The new version must be higher; with no tag, the first release is the version already in `package.json`.
- Bump in a PR when needed: branch `chore/bump-web-<version>` from `origin/main`, run `npm version <version> --no-git-tag-version` in `dari-web/`, commit `chore: bump dari-web to <version>`, wait for the Dari Web CI, merge.
- Publish from a clean `main`: `cd dari-web && npm ci && npm publish --access public`. `prepublishOnly` runs typecheck, tests and the build. The user signs in to npm (`npm login`) themselves.
- Tag the published commit: `git fetch origin && git tag dari-web-v<version> origin/main && git push origin dari-web-v<version>`. The prefix keeps it out of the `x.y.z` tags that `/release-scope` and the desktop release read.
- No GitHub release by default. If the user wants one, pass `--latest=false`: the desktop updater, `install-desktop.sh` and the download page read `/releases/latest`, which must stay on a desktop release.
- Report the npm URL: `https://www.npmjs.com/package/@easyhooon/dari-web/v/<version>`.

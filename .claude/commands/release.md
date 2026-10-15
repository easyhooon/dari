You are releasing a new version of the dari library. The version to release is: $ARGUMENTS (if not provided, read the current version from dari/build.gradle.kts).

Follow these steps:

## Step 1: Verify version
- Read `dari/build.gradle.kts`, `dari-core/build.gradle.kts`, `dari-noop/build.gradle.kts` and confirm the version matches across all modules.
- If a version argument was provided and it doesn't match, update all three build.gradle.kts files to the specified version.

## Step 2: Publish to Maven Central
- Run `./gradlew clean publishAllPublicationsToMavenCentralRepository`
- Verify the build succeeds.

## Step 3: Create GitHub Release
- Run `gh release list --limit 3` to find the previous release tag.
- Run `git log --oneline <previous_tag>..HEAD` to get the commits since last release.
- Run `gh release view <previous_tag>` to reference the existing release note style.
- For each PR merged since the previous tag (the `Merge pull request #N` commits), run `gh pr view <N> --json body` and look for a **Behavior change** note. Commit messages don't carry these, so skipping this step drops changed defaults from the notes.
- Create a release note following the same format:
  - Title: version number (e.g., "1.3.1")
  - Tag: version number
  - Body structure:
    ```
    ## Behavior changes
    * <what changed for existing users, and how to get the old behavior> (<PR link>)

    ## What's Changed
    * <PR/feature title> by @easyhooon in <PR link>
      * <bullet points describing key changes>

    **Full Changelog**: https://github.com/easyhooon/dari/compare/<prev_tag>...<new_tag>
    ```
- Omit `## Behavior changes` when no PR has one.
- Run `gh release create <version> --title "<version>" --notes "<release notes>"`

## Step 4: Report
- Print the release URL and confirm completion.
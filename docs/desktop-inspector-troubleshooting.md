# Desktop Inspector Troubleshooting Log

Problems hit while building and releasing the desktop inspector (#99), with cause, fix and where it
landed. Newest first. Add an entry whenever a problem takes more than a quick look to explain.

Related: [design](desktop-inspector-design.md), [FAQ](desktop-inspector-faq.md).

## Release and packaging

### First launch showed "Dari is damaged and can't be opened"

- **Symptom**: right after 2.0.0, opening Dari once showed the "damaged" dialog (Cancel / Move to
  Trash only, no Open Anyway).
- **Investigation**:
  - The release app's signature verifies, and the download matches its `.sha256`.
  - Reproducing a browser download of the release DMG (quarantined DMG, app copied out) shows the
    normal prompt on macOS 27: "Apple can't check Dari for malicious software". System Settings →
    Privacy & Security → **Open Anyway** opens it. This is the same path Necto, also ad-hoc signed,
    takes.
  - The "damaged" dialog is what macOS shows when a bundle no longer matches its signature. At that
    moment the local `Dari.app` was being deleted and rebuilt, so the likeliest cause is opening a
    half-written build. Not confirmed.
- **Docs**: Open Anyway stays the main first-launch step. `xattr -dr com.apple.quarantine
  /Applications/Dari.app` is the fallback if "damaged" appears.
- **Lesson**: reproduce with the published artifact before changing user-facing install docs.

### Packaged app quit on launch: `NoClassDefFoundError: java/net/http/HttpClient`

- **Cause**: jpackage bundles a trimmed JRE with only the modules Compose expects. The updater's
  `java.net.http` wasn't one of them. `./gradlew run` uses a full JDK, so only the packaged app
  crashed. It was found in the updater's end-to-end test.
- **Fix**: list the modules explicitly in `nativeDistributions { modules(...) }`, taken from
  `./gradlew :dari-desktop-app:suggestRuntimeModules`, plus `jdk.crypto.ec` for TLS. After adding a
  JDK API, rerun that task and launch the packaged app, not just `run`.

### In-app update rolled back: "_LSOpenURLsWithCompletionHandler() failed with error -600"

- **Cause**: the helper relaunched Dari 0.3 s after the old process exited. LaunchServices still
  listed the old Dari as running, so `open` sent a reopen Apple event to the dead process
  (`procNotFound`). The helper then restored 2.1.2 as designed, so the update looked like a no-op.
  Found when updating 2.1.2 to 2.1.3; it reproduced on both attempts. The helper is unchanged since
  2.1.0, so this is a timing race present from the start, not a regression: a busy Mac makes
  LaunchServices slower to drop the old process.
- **Fix**: wait a second after the old process exits, launch with `open -n`, and retry up to five
  times before rolling back. The helper that runs is the one inside the *installed* app, so the fix
  applies to updates from 2.1.4 on; from 2.1.2 or 2.1.3, reinstall once (terminal script or DMG).

### Release run fails at stapling: "The staple and validate action failed! Error 68"

- **Cause**: notarization was already accepted, but `xcrun stapler` lost its connection to Apple's
  ticket service (`NSURLErrorDomain -1005`, "The network connection was lost"). It recurred on the
  hosted runners, most often on Intel. It is a network blip, not a code or signing problem.
- **Fix**: `package-desktop-app.sh` retries staple and validate up to five times with growing waits;
  stapling is idempotent. Before that, re-running the failed job (`gh run rerun <id> --failed`) was
  enough, as for 2.1.3.

### Intel macOS build fails: "code object is not signed at all"

- **Symptom**: the 2.0.0 `Desktop App Release` run failed on `macos-15-intel`, so no installers were attached.
- **Cause**: jpackage signs `Dari.app` ad-hoc only on Apple Silicon, where macOS requires a signature.
  On Intel it leaves the app unsigned. The packaging script only verified the signature. Local builds
  are arm64, so the gap first showed in CI.
- **Fix**: sign ad-hoc when the app isn't signed yet. Don't re-sign arm64 apps: that would drop
  jpackage's JVM entitlements. The release workflow gained a `ref` input so an existing tag can be
  repackaged with fixed scripts. (#107)

### Maven Central upload succeeded but the artifacts stayed at 404

- **Cause**: `publishToMavenCentral()` defaults to `automaticRelease = false`. The upload waits in the
  Central Portal until someone presses Publish.
- **Fix**: `publishToMavenCentral(automaticRelease = true)` in every published module. The
  `mavenCentralAutomaticPublishing` Gradle property isn't enough here: the plugin reads it only when
  publishing is configured through properties. (#106)

### Compose packaging rejects the JDK: "Homebrew's JDK distribution may cause issues"

- **Cause**: the Gradle daemon ran on a Homebrew JDK, and Compose's `checkRuntime` refuses to
  package with it.
- **Fix**: package with the toolchain JDK: `javaHome = javaToolchains.launcherFor { 17 }`. (#100)

### Release tag and installer names could disagree

- **Cause**: the workflow checks out the tag, but the installers take their names from the version in
  `gradle/libs.versions.toml`.
- **Fix**: fail the run when the tag and the catalog version differ. (#103, from CodeRabbit)

## CI

### Linux smoke test fails: "Cannot create Linux GL context"

- **Cause**: xvfb has no GPU. Skiko tries OpenGL, logs a `RenderException`, and falls back to
  software rendering. The smoke test read the word "Exception" as a crash, but the app was fine.
- **Fix**: run the Linux smoke test with `SKIKO_RENDER_API=SOFTWARE`. (#100)

### Windows unit test fails: `locates adb under ANDROID_HOME`

- **Cause**: the test created `platform-tools/adb`, but on Windows the app looks for `adb.exe`.
- **Fix**: the test uses the OS-specific file name. (#100)

### CodeRabbit always said "Review limit reached"

- **Cause**: every push triggered a new review, and several pushes in a row used up the free OSS window.
- **Fix**: `.coderabbit.yaml` with `auto_incremental_review: false`. To ask for a review, comment
  `@coderabbitai review`. (#103)

## Desktop app

### Title bar pickers and buttons ignored clicks

- **Cause**: `WindowDraggableArea` wrapped the whole title bar row. Its drag detector consumed the
  presses before the pickers, ⚙ and Connect/Disconnect saw them. Auto-connect and the menu bar hid the
  problem.
- **Fix**: the drag area covers only the empty middle of the title bar. (#103)

### Background clicks don't reach Compose (automation only)

- **Cause**: background automation sends accessibility actions (AXPress). Compose handles pointer
  events, not AX actions. Menu bar items do accept AX actions.
- **Workaround**: use the menu bar, or bring the window to the front for real clicks. Users aren't
  affected.

### Menu bar menus missing on macOS

- **Cause**: Compose's `MenuBar` installs a Swing `JMenuBar`. Without `apple.laf.useScreenMenuBar`, it
  draws inside the window, where the transparent title bar hides it.
- **Fix**: set `apple.laf.useScreenMenuBar=true` before creating any window. (#100)

### "Read timed out" while the app is open on the phone

- **Cause**: Android freezes cached (background) apps. The socket still accepts the connection, but
  nothing answers the handshake.
- **Fix**: the status reads "app is in the background". Reconnecting resumes once the app is in front
  again. (#100)

### adb hangs freeze device discovery

- **Cause**: reading adb's output before `waitFor(timeout)` blocks until adb exits, so the timeout
  never fires, for example on an unauthorized device.
- **Fix**: drain the output on a separate thread. (#103, from CodeRabbit)

### Stray `adb forward` entries after the app was killed

- **Cause**: tunnels are removed on disconnect, but a killed process never gets there.
- **Fix**: remove every `localabstract:dari_*` forward on start and from a JVM shutdown hook. (#100)

### Settings window opened on another Space, in a screen corner

- **Fix**: `WindowPosition.Aligned(Alignment.Center)`. (#103)

## Library (`dari`)

### Crash when a desktop connects: "removeObserver must be called on the main thread"

- **Cause**: the connected-client count was collected on `Dispatchers.IO`, and pausing shake-to-open
  unregisters a process lifecycle observer, which only the main thread may touch.
- **Fix**: collect the count and apply the change on `Dispatchers.Main`. (#100)

### A client accepted while the server stops could leak

- **Cause**: the accept loop checked `running` and added the client outside the lock that `stop()`
  holds.
- **Fix**: register clients under the same lock, re-checking `running`. (#103, from CodeRabbit)

### The app never sees an `adb forward` connection until the client writes

- **Cause**: adb opens the device-side socket lazily, on the first bytes from the computer. A client
  that waits for a server greeting waits forever.
- **Fix**: the desktop speaks first (`ClientHello`). Found in the milestone 0 spike.

## Testing on devices

### "Notifications pause while connected" looked untestable

- **Cause**: the sample app's `POST_NOTIFICATIONS` permission was off, so no notification ever
  appeared. An earlier check had matched another app's Dari notification by mistake.
- **Fix**: grant the permission on the device, then verify the full show → hide → show cycle.

### Linux GUI couldn't be checked in a cloud sandbox

- **Cause**: the sandbox blocked the Unix socket that a display server needs. Builds and the 22 unit
  tests passed, but no window could open.
- **Status**: Linux stays unreleased until it is tested on a desktop Linux machine.

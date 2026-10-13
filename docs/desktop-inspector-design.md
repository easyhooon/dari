# Desktop Inspector Design (#99)

Status: draft · Scope: macOS first, then Windows, then Linux.

## Goal

Stream Dari bridge messages from a connected Android debug build to a desktop app, live,
with the context Dari already shows (direction, handler, display name, status, tag, payloads,
protobuf metadata).

## Decisions

| Topic | Decision | Why |
| --- | --- | --- |
| Desktop UI | Compose Multiplatform Desktop (JVM) | One UI for all three OSes; reuses `dari-core` (`jvm()` target already exists). |
| Native UI (SwiftUI) | Not used | Would need a second UI for Windows/Linux; the app is a debug tool, so UI polish is secondary. |
| necto integration | Later, as a separate plugin module | Ship a Dari-only desktop first; NectoMac is macOS-only and not ours. |
| Reference | Ideas from toss/necto (MIT), no copied code | Protocol shapes and ideas need no notice; copied code would. |

## Architecture

```
Android debug app                         Desktop (Mac/Win/Linux)
┌──────────────────────────┐              ┌──────────────────────────┐
│ :dari-desktop            │              │ :dari-desktop-app (CMP)  │
│  MessageRepository       │              │  AdbDeviceTracker        │
│   └ events (SharedFlow)  │              │  InspectorClient         │
│  DariInspectorServer ◄───┼── adb fwd ───┼─►  list / observe        │
│   LocalServerSocket      │              │  UI: list + detail       │
└──────────────────────────┘              └──────────────────────────┘
            ▲  both depend on  ▲
            └── :dari-inspector-protocol (wire DTOs, envelope, framing) ──┘
```

### Modules

Planned for 2.0.0. Apps keep a single debug dependency; upgrading `dari` is all it takes:

```kotlin
debugImplementation("io.github.easyhooon:dari:2.0.0")
releaseImplementation("io.github.easyhooon:dari-noop:2.0.0")
```

- `:dari-runtime` (new): capture, Room storage, `DefaultDariInterceptor`. Shared code is public but
  gated by `@InternalDariApi` (opt-in required), since Kotlin `internal` doesn't cross modules.
- `:dari`: runtime + in-app UI, notifications, shake, export, and the desktop socket server.
  While a desktop is connected, the in-app notification and shake stay quiet; they resume on
  disconnect. `Dari.setDesktopInspectorEnabled(false)` turns the socket off.
- `:dari-noop`: release builds. Depends only on `dari-core`.
- `:dari-inspector-protocol` (new, plain Kotlin/JVM): wire DTOs, envelope, framing.
- `:dari-desktop-app` (new, Compose Desktop app; shipped as `.dmg`/`.msi`/`.deb`, not to Maven Central).
- `:dari-core`: only gains `DariPayloadRedactor` (additive).

`dari` and `dari-noop` expose the same `Dari` API (`./gradlew checkDariApiParity`).

A separate `dari-desktop` artifact was tried and dropped: existing users would have had to swap
dependencies to try the desktop app, and the only thing it bought (no socket in debug builds) is
covered by `setDesktopInspectorEnabled(false)`.

### Release and data protection

- Release uses `dari-noop`: verified the sample release APK contains no Room, `LocalServerSocket`,
  inspector or runtime classes, and no `dari_` socket name.
- The socket opens only in debuggable builds, or when a non-debuggable internal build opts in with
  manifest meta-data `com.easyhooon.dari.desktop.ALLOW_NON_DEBUGGABLE`. In-app capture is unaffected.
- Only adb (uid 2000) or root may connect; no INTERNET permission.
- Built-in masking before anything leaves the device: sensitive JSON keys at any depth (including
  stringified JSON), `key: value` / `key=value` text (decoded protobuf, query strings), `Bearer`/`Basic`
  credentials. Raw protobuf previews are dropped. Masking fails closed. `Dari.setPayloadRedactor`
  runs after it; if it throws, the message is not sent.
- Nothing logs payloads; the server logs only rejected peer UIDs.

### Why not read logcat instead

A dependency-free option was considered: the desktop app detects devices over adb and filters
`adb logcat` for bridge messages. It doesn't replace the `dari` library:

- **Bridge calls aren't in logcat.** They are in-process calls between JavaScript and the app
  (`@JavascriptInterface`, `evaluateJavascript`). Nothing reaches logcat unless the app logs each
  call itself, so app code changes are needed anyway.
- **Payloads get cut.** logcat truncates lines at about 4 KB and drops chatty output, so large
  request/response bodies arrive incomplete.
- **No structure.** Pairing requests with responses, status and duration would have to be
  re-parsed from free-form text.
- **Leaks into release builds.** `Log` calls left in the app ship in release builds, unmasked, and
  any adb client can read them. `dari-noop` contains no capture code, and the desktop channel
  masks tokens, passwords and cookies before data leaves the device.

## Transport

Reversed roles, as in necto: the app listens, the desktop connects.

- App: `LocalServerSocket` in the abstract namespace, name `dari_<applicationId>`.
  No INTERNET permission needed (`:dari` has none today), never reachable over Wi-Fi.
- Desktop: `adb forward tcp:0 localabstract:dari_<applicationId>`, then TCP to `127.0.0.1:<port>`.
- Discovery: poll `adb devices -l` every 2 s; per device, list `dari_*` sockets from
  `/proc/net/unix` via `adb shell` to find apps running Dari (an applicationId can also be typed in).
  When exactly one is found the desktop connects on its own, and reconnects every 2 s after a drop.
- Access control: on accept, `LocalSocket.getPeerCredentials()` and allow only UID 2000 (shell)
  or 0 (root). Blocks other apps on the device from connecting.
  Verified on Galaxy S25+ (Android 17): an `adb forward` connection reports `uid=2000`
  (the adbd process), and no SELinux denial occurs. adb connects to the app socket lazily,
  so the desktop must send data (the handshake) before the app sees the connection.
- adb: shell out to the `adb` binary (`ANDROID_HOME`/`ANDROID_SDK_ROOT`/`PATH`, user-overridable).
  `ANDROID_SERIAL` picks the device first, as it does for adb.

### How the connection works (`adb forward`)

`adb forward` is a USB tunnel: it opens a port on the computer and pipes it to a socket inside the
device. Dari's app socket has no port at all, only a name, so it can't be reached any other way.

```
Desktop app ──TCP──► 127.0.0.1:56778 ══ adb over USB ══► @dari_com.example.app (inside the app)
                     (picked by adb)                       (abstract socket, no port)
```

1. The app opens the abstract socket `dari_<applicationId>` and waits.
2. The desktop runs `adb forward tcp:0 localabstract:dari_<applicationId>`. `tcp:0` lets the OS pick
   a free local port, and adb prints it.
3. The desktop connects to `127.0.0.1:<that port>`; adb carries the bytes to the app socket.
4. On disconnect the desktop runs `adb forward --remove tcp:<port>`.

`adb forward --list` shows the open tunnels. If the desktop app is killed before step 4, its
tunnels stay behind (harmless, just clutter), so it also removes every `localabstract:dari_*`
forward when it starts and when the JVM exits. Emulators are adb devices too, so they work the same.

**Nothing to configure.** Compared with an HTTP proxy such as Charles:

| | Charles | Dari |
| --- | --- | --- |
| What it intercepts | Network traffic, by sitting between the device and the internet | In-process WebView bridge calls, which never touch the network |
| Device setup | Wi-Fi proxy host and port, user CA certificate | None |
| App setup | `network_security_config` trusting user CAs (for HTTPS) | The `dari` debug dependency |
| Same Wi-Fi needed | Yes | No, USB or any adb connection |
| Ports to match | Proxy port on device and computer | None; adb picks one |

Charles needs no library because it intercepts the network; bridge calls are plain function calls
between JavaScript and the app, so only code inside the app can see them.

## Protocol (v1)

- Framing: 4-byte big-endian length + UTF-8 JSON. Max frame 4 MiB (payloads are already
  truncated by `DariConfig.maxContentLength`).
- Handshake: desktop sends `ClientHello {protocolVersion, clientName, clientVersion}` first (adb opens
  the app socket only once the client writes); app replies `ServerHello {accepted, protocolVersion,
  rejection?, app {applicationId, appName, appVersion, dariVersion, deviceModel, sdkInt}}`.
  Exact version match.
- Envelope: `{type, payload}` with `invoke {requestId, op, input}`, `result {requestId, output?,
  error?, isFinal}`, `cancel {requestId}`.
- Operations:
  - `messages.list {limit}` → summaries (no payload bodies)
  - `messages.detail {id}` → full request/response data + payload metadata
  - `messages.observe` (stream) → `added | updated | cleared` with summaries
  - `messages.clear`
- Error codes: `INVALID_INPUT`, `OPERATION_NOT_FOUND`, `CANCELLED`, `PROVIDER_FAILED`, `TIMEOUT`.
- Client order: subscribe to `observe` first, then `list`, merge by id (absorbs overlap).

### Stable wire id

`MessageRepository.addEntry` gives a negative temp id and swaps in the Room id later, so
upsert-by-id on the desktop would duplicate rows. The repository keeps a `DB id → temp id` map and
exposes entries as `InspectorEntry(wireId, entry)`; the wire `id` is the temp id for entries
created in this process and the Room id for entries restored from disk. Mutations and their events
happen under one lock, so events and snapshots agree. Trimmed entries are not sent as removals.

## Runtime and server

1. `MessageRepository`: internal `SharedFlow<RepositoryEvent>` (`Added`, `Updated`, `Cleared`)
   alongside the existing `StateFlow`; `list` serves the current snapshot.
2. `DariInspectorServer`: starts in `Dari.init` only when enabled; one session per client.
3. The server starts from `Dari.init` in `dari`; `Dari.setPayloadRedactor` adds custom masking and
   `Dari.setDesktopInspectorEnabled` turns the socket on or off. Not in `DariConfig`: it lives in published `dari-core`, and adding a field would break
   its binary compatibility.

## Desktop app

- Layout: device/app picker · message list (filter by handler, direction, status, tag) · detail
  pane with JSON folding and protobuf preview. UI code is copied from `:dari` and adapted
  (AndroidX Compose ≠ JetBrains Compose); a shared UI module is a later step.
- macOS feel: `MenuBar` (native menu bar), `Cmd` shortcuts, `apple.awt.fullWindowContent` +
  `apple.awt.transparentTitleBar`, follow system dark mode, AWT `FileDialog`, run on JetBrains Runtime.
- Later: agent-facing CLI (`dari-desktop list/observe --json`), as necto does.

## Milestones

| # | Scope | Estimate |
| --- | --- | --- |
| 0 | Spike: abstract socket + peer UID check over `adb forward` on a real device | done |
| 1 | Protocol module + repository events + server + opt-in/redactor (+ noop) + tests | done |
| 2 | Desktop MVP via `./gradlew :dari-desktop-app:run`: one device, list/detail/live | done |
| 3 | macOS packaging: ad-hoc signed `.dmg` (arm64, x64) + SHA-256, release workflow, install docs | done |
| 4 | Windows (`.msi`) and Linux (`.deb`): packaging and CI smoke tests done; released after testing on real machines | in progress |
| 5 | Optional necto plugin module | separate issue |

## Open checks

- JetBrains Compose plugin version compatible with Kotlin 2.2.10 / AGP 9.0.0.
- Whether root detekt/ktlint config works for a JVM desktop module.
- Where the desktop app is distributed (GitHub Releases is the default assumption).

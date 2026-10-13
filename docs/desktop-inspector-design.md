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
│ :dari                    │              │ :dari-desktop (CMP)      │
│  MessageRepository       │              │  AdbDeviceTracker        │
│   └ events (SharedFlow)  │              │  InspectorClient         │
│  DariInspectorServer ◄───┼── adb fwd ───┼─►  list / observe        │
│   LocalServerSocket      │              │  UI: list + detail       │
└──────────────────────────┘              └──────────────────────────┘
            ▲  both depend on  ▲
            └── :dari-inspector-protocol (wire DTOs, envelope, framing) ──┘
```

### Modules

- `:dari-inspector-protocol` (new, plain Kotlin/JVM, usable from Android): wire DTOs, envelope, handshake,
  length-prefixed framing, kotlinx.serialization. Template: `ExportableMessage`.
- `:dari` (changed): event stream in `MessageRepository`, `DariInspectorServer`, opt-in config.
- `:dari-noop` (changed): same new public API, no-op.
- `:dari-desktop` (new, CMP desktop app, not published to Maven Central).
- `:dari-core`: only gains `DariPayloadRedactor` (additive). No `@Serializable`, no new deps — it is
  published, dependency-free, and targets iOS. Keeping the wire format out of it avoids coupling the
  public model to the protocol.

## Transport

Reversed roles, as in necto: the app listens, the desktop connects.

- App: `LocalServerSocket` in the abstract namespace, name `dari_<applicationId>`.
  No INTERNET permission needed (`:dari` has none today), never reachable over Wi-Fi.
- Desktop: `adb forward tcp:0 localabstract:dari_<applicationId>`, then TCP to `127.0.0.1:<port>`.
- Discovery: `adb track-devices` for attach/detach; per device, list `dari_*` sockets from
  `/proc/net/unix` via `adb shell` to find debuggable apps; retry connect every 2 s.
- Access control: on accept, `LocalSocket.getPeerCredentials()` and allow only UID 2000 (shell)
  or 0 (root). Blocks other apps on the device from connecting.
  Verified on Galaxy S25+ (Android 17): an `adb forward` connection reports `uid=2000`
  (the adbd process), and no SELinux denial occurs. adb connects to the app socket lazily,
  so the desktop must send data (the handshake) before the app sees the connection.
- adb: shell out to the `adb` binary (`ANDROID_HOME`/`ANDROID_SDK_ROOT`/`PATH`, user-overridable).

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

## `:dari` changes

1. `MessageRepository`: internal `SharedFlow<RepositoryEvent>` (`Added`, `Updated`, `Cleared`)
   alongside the existing `StateFlow`; `list` serves the current snapshot.
2. `DariInspectorServer`: starts in `Dari.init` only when enabled; one session per client.
3. `Dari.startDesktopInspector(redactor: DariPayloadRedactor? = null)` / `stopDesktopInspector()`,
   mirrored in `:dari-noop`. Not in `DariConfig`: it lives in published `dari-core`, and adding a
   field would break its binary compatibility.

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
| 2 | Desktop MVP via `./gradlew :dari-desktop:run`: one device, list/detail/live | 3–4 days |
| 3 | macOS packaging: `.dmg`, signing, notarization | 1–2 days |
| 4 | Windows (`.msi`), then Linux (`.deb`/AppImage) | 1–2 days each |
| 5 | Optional necto plugin module | separate issue |

## Open checks

- JetBrains Compose plugin version compatible with Kotlin 2.2.10 / AGP 9.0.0.
- Whether root detekt/ktlint config works for a JVM desktop module.
- Where the desktop app is distributed (GitHub Releases is the default assumption).

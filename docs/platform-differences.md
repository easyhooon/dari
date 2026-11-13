# Android and iOS Differences

Where the Android library (`dari`) and the iOS SDK (`Dari` Swift package) behave differently, why, and whether
the gap is intended. For maintainers: check this before porting a feature, so an intended difference isn't
"fixed" by accident. As of 3.5.0 (iOS) and 3.0.0 (Android library). Update it when either side changes.

Status: **Intended** means keep it as is. **Gap** means not ported yet; follow the linked issue.

Related: [desktop inspector design](desktop-inspector-design.md), user-facing table in the README's Platform
Support section.

## In-app inspector

| | Android | iOS (`DariUI`) | Status |
| --- | --- | --- | --- |
| Opening it | Ongoing notification, shake, launcher shortcut | `Dari.shared.presentInspector()` from the app's debug menu, or `DariInspectorView` in its own sheet | Gap: floating button and shake in #288 step 2. No ongoing notification on iOS |
| While a desktop is connected | Notification and shake pause, resume on disconnect | Nothing to pause yet | Gap: mirror it when step 2 adds entry points (#288) |
| Filters | Status, handler, tag chips plus search | Search only | Gap (#288) |
| Response-time stats per handler | Yes | No | Gap (#288 step 2). The desktop app already has the screen |
| Masking switch | `Dari.setMaskingEnabled`, settings sheet | **Mask Sensitive Values** in the ⋯ menu | Intended: same rules, different entry. No public API on iOS yet |
| Save to a chosen location | Share or Save (SAF) | Share sheet only, which offers Save to Files | Intended |
| History after relaunch | Kept (Room), always | Kept with `persistsMessages: true` (3.6.0+), off by default | Intended (#300). See below |
| Response after relaunch with a reused callback id | Can complete a request from the earlier launch | Only completes requests from this launch | Intended (#300) |
| Total size cap | None | None | Gap on both: #299 |
| Look | Material | System components; Liquid Glass on iOS 26+ | Intended |

## SDK and integration

| | Android | iOS | Status |
| --- | --- | --- | --- |
| Minimum OS | Android 8.0 (API 26) | iOS 16 (15 up to 3.4.0) | Intended. Lower only on request |
| Distribution | Maven Central, `dari` + `dari-noop` | SPM from git tags | Intended |
| Release builds | `dari-noop`: same API, no capture code | Call `start()` only under `#if DEBUG` | Intended |
| Capture without manual calls | Gradle plugin rewrites bytecode at build time | `autoCapture` (WebKit swizzling) or `@DariBridge` macro | Intended: different platform tools |
| Protobuf without manual calls | Gradle plugin | Manual `…Protobuf…` calls only | Gap: `autoCapture` / `@DariBridge` record strings (noted in #293) |
| What a payload redactor can change | Any field (`copy`) | `requestData`, `responseData`, `pageUrl` | Intended, see below |
| Redactor throws | The `list`/`detail` request fails, or the observe stream ends | That message's editable fields are hidden; the rest still flows | Intended, see below |
| Raw protobuf previews with a redactor set | Sent unless the redactor drops them | Never sent | Intended, see below |
| Messages dropped by `retentionPeriod` / `maxEntries` | No event to the desktop | No event to the desktop | Same on purpose (#286). A removal event would need a protocol change on both sides |
| Desktop connection | adb: USB or Wi-Fi pairing | macOS only: Simulator or USB via usbmuxd | Gap: Wi-Fi in #281 |
| Desktop app OS | macOS, Windows, Linux | macOS | Follows from the row above |

## Why

### iOS persistence is opt-in

The iOS SDK started (3.0) desktop-first and memory-only: the desktop app holds the history. With `DariUI` (3.5.0) a
QA tester needs what happened before a crash, so 3.6.0 adds `persistsMessages` (#300). It stays off by default
because payloads, stored as recorded, can hold tokens; the file is excluded from backups and protected until first
unlock. Stored messages load in the background, like Android's Room load, and responses never pair with them. A
total size cap for both platforms is #299.

### Android needs stable wire ids, iOS doesn't

Android shows a message under a temporary id, then Room replaces it with the persisted id.
`MessageRepository.wireIds` keeps the desktop on the first id so it never sees one message twice (#100). The
iOS store assigns each id once and never changes it. Both inspectors read the same store on both platforms,
so connecting the desktop and opening the in-app screen never records anything twice.

### The iOS redactor is narrower and fails closed per message

- `DariMessage` exposes only the payloads and page URL as editable. A throwing redactor then hides exactly
  those fields, which keeps the fail-closed rule simple. Handler names can't be rewritten; `masking.mdx` says so.
- Android lets the exception fail the whole `list`/`detail` request or end the observe stream. iOS hides that
  one message instead, so one bad record doesn't blank the desktop list.
- A redactor can't see raw protobuf bytes, so iOS drops raw previews whenever a redactor is set (#293), the
  same reason masking drops them.

### iOS in-app UI is Swift, not KMP

Compose Multiplatform could draw the screens, but it wouldn't get system components or Liquid Glass, and it
would add the Kotlin/Native runtime to the app and turn the SPM source package into a binary XCFramework
release. Screens aren't shared; data formats are: the export format and the wire protocol are pinned by tests
(`IosWireFixturesTest` and the Swift `WireFixturesTests`, `InspectorFormatTests`).

## When porting a feature

1. Check the tables above; if the difference is **Intended**, keep it.
2. Match data formats exactly (export fields, key order, nulls; wire fixtures), not the UI.
3. Update this file, the README Platform Support table, and the iOS guide in the same PR.

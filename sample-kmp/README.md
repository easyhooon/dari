# sample-kmp

Sample for [#13](https://github.com/easyhooon/dari/issues/13), explained in [docs/kmp-swift-bridge.md](../docs/kmp-swift-bridge.md): a `commonMain` bridge (`SharedBridge`) records through `dari-kmp`, which forwards to the Swift SDK on iOS and to `dari` on Android.

This module is the shared code of a KMP app: `sample` plays the Android app and `sample-ios` the iOS app.

## Android

`sample` depends on this module and calls `SharedBridge` in `MainActivity`. Debug and staging builds (`dari`) show the `kmp` records in the Dari UI; release (`dari-noop`) records nothing.

## iOS

Run it on the booted iOS Simulator with:

```bash
sample-kmp/run-on-simulator.sh
```

The script links `DariKmpSample` through `xcodebuild` overrides, so `sample-ios` itself needs no Gradle or JDK. The plain sample build skips the Kotlin code (`#if canImport(DariKmpSample)`).

Launching the app records `getUser`, `onThemeChanged` and `logEvent` with tag `kmp`.

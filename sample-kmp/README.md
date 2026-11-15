# sample-kmp

Sample for [#13](https://github.com/easyhooon/dari/issues/13), explained in [docs/kmp-swift-bridge.md](../docs/kmp-swift-bridge.md): a `commonMain` bridge (`SharedBridge`) records through `dari-kmp`, which forwards to the Swift SDK on iOS.

Run it on the booted iOS Simulator with:

```bash
sample-kmp/run-on-simulator.sh
```

The script links `DariKmpSample` through `xcodebuild` overrides, so `sample-ios` itself needs no Gradle or JDK. The plain sample build skips the Kotlin code (`#if canImport(DariKmpSample)`).

Launching the app records `getUser`, `onThemeChanged` and `logEvent` with tag `kmp`.

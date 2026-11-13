# sample-kmp

PoC for [#13](https://github.com/easyhooon/dari/issues/13): a `commonMain` bridge (`SharedBridge`) records through `dari-kmp`, which forwards to the Swift SDK on iOS.

Run it in `sample-ios` on a Simulator. The plain sample build skips it (`#if canImport(DariKmpSample)`).

```bash
./gradlew :sample-kmp:linkDebugFrameworkIosSimulatorArm64
cd sample-ios && xcodebuild -project DariSample.xcodeproj -scheme DariSample -destination 'platform=iOS Simulator,name=<device>' \
  "FRAMEWORK_SEARCH_PATHS=\$(inherited) $PWD/../sample-kmp/build/bin/iosSimulatorArm64/debugFramework" \
  "OTHER_LDFLAGS=\$(inherited) -framework DariKmpSample" build
```

Launching the app records `getUser`, `onThemeChanged` and `logEvent` with tag `kmp`.

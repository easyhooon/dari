# Compatibility checks

CI (`.github/workflows/kmp-core.yml`) guards `dari-core` consumers in two ways. Neither needs signing credentials.

## ABI baseline

`dari-core/api/` holds the public ABI dumps (`jvm/dari-core.api` for JVM/Android, `dari-core.klib.api` for iOS).
`checkLegacyAbi` fails when the compiled API differs from them.

```bash
./gradlew :dari-core:checkLegacyAbi
```

After an intended API change, regenerate the dumps and commit them with the change:

```bash
./gradlew :dari-core:updateLegacyAbi
```

## External Android consumer

`android-consumer/` is a standalone Gradle build that depends on `io.github.easyhooon:dari-core:<version>` from an isolated local repository (`build/compat-repo`), never from Maven Central or a project dependency.
It checks that Gradle metadata resolves the root coordinate to `dari-core-android`, then compiles code using the public API.

```bash
./gradlew -PskipPublicationSigning :dari-core:publishAllPublicationsToCompatRepository
./gradlew -p compat/android-consumer assembleDebug
```

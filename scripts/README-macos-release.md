# macOS Developer ID releases

`desktop-release.yml` builds arm64 and x64 on separate macOS runners with JDK 17.
macOS release packaging requires Developer ID credentials and fails instead of falling
back to ad-hoc signing. Local smoke tests still work without release credentials.

## Repository secrets

Add these under Settings → Secrets and variables → Actions → New repository secret:

| Name | Value |
| --- | --- |
| `MACOS_CERTIFICATE_P12_BASE64` | Base64 of a password-protected `.p12` containing a Developer ID Application certificate **and its private key** |
| `MACOS_CERTIFICATE_PASSWORD` | The password chosen when exporting that `.p12` |
| `APPLE_ID` | The Apple Account email with access to the Developer team |
| `APPLE_APP_SPECIFIC_PASSWORD` | An app-specific password from account.apple.com → Sign-In and Security |
| `APPLE_TEAM_ID` | The 10-character Team ID from developer.apple.com/account → Membership details |

The certificate team and notarization team must match. `GITHUB_TOKEN` is supplied by
Actions; no additional GitHub token is needed. An Apple Distribution or Developer ID
Installer certificate does not replace Developer ID Application for these DMGs.

Users must handle certificate creation, private-key export, app-password issuance and
GitHub secret entry themselves. A `.cer` alone contains no private key. Before issuing
a new certificate, check Keychain Access → login → My Certificates and expand the
Developer ID Application entry to confirm its corresponding private key.

If an existing App Store Connect API key is suitable for notarization, reuse it rather
than issuing another credential. This workflow currently implements the five-secret
Apple Account contract above; it does not yet support API-key authentication. Never
paste a `.p12`, `.p8`, password or Base64 credential into issues or release notes.

## Signing and verification

Compose 1.9.3 signs native libraries inside JARs, and JDK 17 signs other native files.
Compose then re-signs the runtime. `macos-entitlements.plist` retains JDK 17's default
non-App-Store entitlements for both the app and runtime, including JVM JIT permissions.
Signing uses a disposable CI keychain, an identity selected for `APPLE_TEAM_ID`,
hardened runtime and secure timestamps. It never imports into the login keychain.

The pipeline verifies the Developer ID chain, team and JVM entitlements, launches the
signed app, notarizes and staples the app, then creates, signs, notarizes and staples
the DMG. Only after those steps does it copy the DMG into `build/release` and generate
its `.sha256`. The temporary keychain and credential files are removed even on failure.

The DMG window layout (background arrow, icon positions) comes from `dmgbuild` with
`dari-desktop-app/packaging/dmg/dmg-settings.py`. Run `scripts/make-dmg-background.py`
(needs Pillow) to redraw the background, and keep its icon positions in sync.

## Before publishing 2.1.1

1. Run unit and packaging contract tests. Build installers from a reviewed commit whose
   catalog version matches the tag; build outputs and GitHub assets must use `2.1.1`.
2. On real arm64 and Intel Macs, download the final DMG through a browser, verify its
   SHA-256, and run `codesign`, `spctl` and `xcrun stapler validate` on the installed app
   and DMG. Check both online and offline first launch, the inspector and ADB connection.
3. Starting with the actual public 2.1.0 app, install the 2.1.1 candidate through the
   updater. Verify relaunch, version, settings, signature and ticket preservation.
   Use a controlled release source for pre-publication testing; drafts are not visible
   to the production `/releases/latest` updater. Never replace the public 2.1.0 assets.
4. Check rejection of checksum mismatch, invalid signatures, unexpected bundle IDs,
   version mismatch and downgrade; verify failure leaves the old app usable.
5. Publish only after both architecture checks pass. The existing updater validates
   the signature's integrity but does not pin a Developer Team ID; that trust-policy
   change is separate from this signing migration.

No local credential import, signing, Apple upload or GitHub release is necessary to run
`python3 scripts/test-macos-packaging.py`; it uses disposable fixtures and mocked tools.

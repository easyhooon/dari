#!/usr/bin/env bash
# Imports CI credentials into a disposable runner keychain, never the login keychain.
set +x
set -euo pipefail
umask 077

for variable in RUNNER_TEMP GITHUB_ENV MACOS_CERTIFICATE_P12_BASE64 MACOS_CERTIFICATE_PASSWORD \
    APPLE_ID APPLE_APP_SPECIFIC_PASSWORD APPLE_TEAM_ID; do
  [ -n "${!variable:-}" ] || { echo "Missing $variable for macOS signing." >&2; exit 1; }
done
[[ "$APPLE_TEAM_ID" =~ ^[A-Z0-9]{10}$ ]] || { echo "Invalid Apple Team ID." >&2; exit 1; }

keychain="$RUNNER_TEMP/dari-signing.keychain-db"
certificate="$RUNNER_TEMP/dari-signing.p12"
auth_log="$RUNNER_TEMP/dari-notary-auth.log"
trap 'rm -f "$certificate" "$auth_log"' EXIT
[ ! -e "$keychain" ] || { echo "The temporary signing keychain already exists." >&2; exit 1; }
printf 'MACOS_SIGNING_KEYCHAIN=%s\nMACOS_NOTARY_PROFILE=dari-release\n' "$keychain" >> "$GITHUB_ENV"

python3 - "$certificate" <<'PY'
import base64, os, sys
try:
    data = base64.b64decode("".join(os.environ["MACOS_CERTIFICATE_P12_BASE64"].split()), validate=True)
    if not data:
        raise ValueError()
except ValueError:
    sys.exit("The signing certificate must contain valid Base64 data.")
with open(sys.argv[1], "wb") as certificate:
    certificate.write(data)
PY

keychain_password="$(uuidgen)$(uuidgen)"
echo "::add-mask::$keychain_password"
security create-keychain -p "$keychain_password" "$keychain" > /dev/null 2>&1
security set-keychain-settings -lut 7200 "$keychain" > /dev/null 2>&1
security unlock-keychain -p "$keychain_password" "$keychain" > /dev/null 2>&1
if ! security import "$certificate" -k "$keychain" -P "$MACOS_CERTIFICATE_PASSWORD" \
    -T /usr/bin/codesign -T /usr/bin/security > /dev/null 2>&1; then
  echo "Could not import the certificate and private key into the temporary keychain." >&2
  exit 1
fi
security set-key-partition-list -S apple-tool:,apple:,codesign: -s -k "$keychain_password" "$keychain" > /dev/null 2>&1

# Select one valid Developer ID Application identity from this keychain and the requested team.
identity="$(security find-identity -v -p codesigning "$keychain" | python3 -c '
import os, re, sys
team = os.environ["APPLE_TEAM_ID"]
identities = re.findall(r"^\s*\d+\) [0-9A-Fa-f]{40} \"(Developer ID Application: [^\"\n]+)\"", sys.stdin.read(), re.M)
identities = [identity for identity in identities if identity.endswith("(" + team + ")")]
if len(identities) != 1:
    sys.exit("Expected one valid Developer ID Application identity for the requested team.")
print(identities[0])
')"
printf 'MACOS_SIGNING_IDENTITY=%s\n' "$identity" >> "$GITHUB_ENV"

# Suppress auth diagnostics; they can include the account identifier.
if ! xcrun notarytool store-credentials dari-release --apple-id "$APPLE_ID" \
    --password "$APPLE_APP_SPECIFIC_PASSWORD" --team-id "$APPLE_TEAM_ID" \
    --keychain "$keychain" > "$auth_log" 2>&1; then
  echo "Could not validate the Apple notarization credentials." >&2
  exit 1
fi

#!/usr/bin/env python3
"""Release contract tests using mocked Apple tools, never real credentials or uploads."""
import base64
import hashlib
import json
import os
from pathlib import Path
import plistlib
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parent.parent
TEAM = "TESTTEAM01"
STUB = r'''#!/usr/bin/env python3
import json, os, pathlib, plistlib, sys
tool = pathlib.Path(sys.argv[0]).name
args = sys.argv[1:]
with open(os.environ["TOOL_LOG"], "a") as log:
    log.write(json.dumps([tool] + args) + "\n")
if tool == "uname":
    print("Darwin" if args == ["-s"] else os.environ.get("TEST_ARCH", "arm64"))
elif tool == "gradlew":
    assert os.environ.get("DARI_MACOS_SIGN") == "true"
    app = pathlib.Path("dari-desktop-app/build/compose/binaries/main-release/app/Dari.app")
    (app / "Contents/runtime/Contents/Home/lib/server").mkdir(parents=True)
    (app / "Contents/runtime/Contents/Home/lib/server/libjvm.dylib").touch()
    (app / "Contents/Info.plist").touch()
elif tool == "plutil":
    print(os.environ.get("TEST_APP_VERSION", "2.1.1") if "CFBundleShortVersionString" in args
          else "io.github.easyhooon.dari.desktop")
elif tool == "codesign":
    if "--test-requirement" in args:
        # A requirement without "=" is read as a file path by codesign.
        assert args[args.index("--test-requirement") + 1].startswith("=")
    if "--verify" in args and os.environ.get("FAIL_SIGNATURE") == "1":
        sys.exit(1)
    if "--entitlements" in args:
        assert "--xml" in args
        if not args[-1].endswith(".app"):
            sys.exit(0)
        with open("dari-desktop-app/packaging/macos-entitlements.plist", "rb") as expected:
            entitlements = plistlib.load(expected)
        if os.environ.get("MISSING_JIT") == "1":
            entitlements.pop("com.apple.security.cs.allow-jit")
        sys.stdout.buffer.write(plistlib.dumps(entitlements))
    elif "--verbose=4" in args:
        team = "OTHERTEAM1" if os.environ.get("WRONG_TEAM") == "1" else "TESTTEAM01"
        flags = "adhoc" if os.environ.get("ADHOC_SIGNATURE") == "1" else "runtime"
        print("flags=0x10000(" + flags + ")\nTimestamp=Test\nTeamIdentifier=" + team, file=sys.stderr)
    elif "--sign" in args and args[-1].endswith(".dmg"):
        with open(args[-1], "ab") as dmg:
            dmg.write(b"-signed")
elif tool == "ditto":
    if "-c" in args:
        pathlib.Path(args[-1]).write_bytes(b"app-archive")
    else:
        import shutil
        shutil.copytree(args[-2], args[-1])
elif tool == "dmgbuild":
    assert args[args.index("-s") + 1].endswith("dmg-settings.py")
    app = pathlib.Path(next(arg[4:] for arg in args if arg.startswith("app=")))
    assert (app / "Contents/stapled-ticket").exists()
    pathlib.Path(args[-1]).write_bytes(b"disk-image")
elif tool == "xcrun":
    if args[:2] == ["notarytool", "submit"]:
        kind = "dmg" if args[2].endswith(".dmg") else "app"
        status = "Invalid" if os.environ.get("REJECT_NOTARY") == kind else "Accepted"
        print(json.dumps({"status": status, "id": "00000000-0000-0000-0000-000000000000"}))
        if os.environ.get("FAIL_NOTARY") == kind:
            sys.exit(1)
    elif args[:2] == ["stapler", "staple"]:
        if os.environ.get("FAIL_STAPLE") == "1":
            sys.exit(1)
        # FLAKY_STAPLE=N fails the first N staple calls, like a dropped connection to Apple.
        flaky = pathlib.Path(os.environ["TOOL_LOG"] + ".flaky")
        failures = int(flaky.read_text()) if flaky.exists() else 0
        if failures < int(os.environ.get("FLAKY_STAPLE", "0")):
            flaky.write_text(str(failures + 1))
            sys.exit(68)
        target = pathlib.Path(args[-1])
        if target.suffix == ".dmg":
            with target.open("ab") as dmg:
                dmg.write(b"-stapled")
        else:
            (target / "Contents/stapled-ticket").touch()
    elif args[:2] == ["stapler", "validate"]:
        kind = "dmg" if args[-1].endswith(".dmg") else "app"
        if os.environ.get("FAIL_VALIDATE") == kind:
            sys.exit(1)
    elif args[:2] == ["notarytool", "store-credentials"]:
        if os.environ.get("FAIL_AUTH") == "1":
            print("Error: HTTP status code: 401 for " + os.environ["APPLE_ID"] + " " +
                  os.environ["APPLE_APP_SPECIFIC_PASSWORD"], file=sys.stderr)
            sys.exit(1)
elif tool == "spctl":
    kind = "dmg" if args[-1].endswith(".dmg") else "app"
    if os.environ.get("FAIL_ASSESS") == kind:
        sys.exit(1)
elif tool == "security":
    if args[0] == "create-keychain":
        pathlib.Path(args[-1]).touch()
    elif args[0] == "find-identity":
        identity = "Apple Distribution" if os.environ.get("WRONG_CERTIFICATE") == "1" else "Developer ID Application"
        count = int(os.environ.get("IDENTITY_COUNT", "1"))
        for index in range(count):
            print(f' {index + 1}) ' + "A" * 40 + f' "{identity}: Example (TESTTEAM01)"')
    elif args == ["list-keychains", "-d", "user"]:
        print('    "/Users/runner/Library/Keychains/login.keychain-db"')
elif tool == "uuidgen":
    print("dummy-ephemeral-keychain-password")
elif tool == "smoke-test-desktop-app.sh":
    assert args == ["--skip-build"]
'''


class MacOSPackagingTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        (self.root / "scripts").mkdir()
        (self.root / "gradle").mkdir()
        (self.root / "dari-desktop-app/packaging").mkdir(parents=True)
        self.restore_entitlements()
        (self.root / "gradle/libs.versions.toml").write_text('dari = "2.1.1"\n')
        for name in ("package-desktop-app.sh", "verify-macos-signature.sh", "setup-macos-signing.sh"):
            shutil.copy2(ROOT / "scripts" / name, self.root / "scripts" / name)
        self.bin = self.root / "bin"
        self.bin.mkdir()
        stub = self.bin / "stub"
        stub.write_text(STUB)
        stub.chmod(0o755)
        for tool in ("uname", "plutil", "codesign", "ditto", "dmgbuild", "xcrun", "spctl", "security", "uuidgen"):
            (self.bin / tool).symlink_to(stub)
        (self.root / "gradlew").symlink_to(stub)
        (self.root / "scripts/smoke-test-desktop-app.sh").symlink_to(stub)
        self.log = self.root / "tools.jsonl"
        self.environment = os.environ.copy()
        # Do not pass any real release credentials from the caller to the fixtures.
        self.environment.update({
            "PATH": str(self.bin) + os.pathsep + os.environ["PATH"],
            "TOOL_LOG": str(self.log),
            "MACOS_SIGNING_IDENTITY": "Developer ID Application: Example (" + TEAM + ")",
            "MACOS_SIGNING_KEYCHAIN": str(self.root / "dummy.keychain-db"),
            "MACOS_NOTARY_PROFILE": "dummy-profile",
            "APPLE_TEAM_ID": TEAM,
            "APPLE_ID": "dummy@example.invalid",
            "APPLE_APP_SPECIFIC_PASSWORD": "dummy-app-password",
            "MACOS_CERTIFICATE_P12_BASE64": base64.b64encode(b"dummy-p12-fixture").decode(),
            "MACOS_CERTIFICATE_PASSWORD": "dummy-p12-password",
            "GITHUB_ENV": str(self.root / "github-env"),
            "RUNNER_TEMP": str(self.root),
            "STAPLE_RETRY_DELAY": "0",
        })

    def run_script(self, name="package-desktop-app.sh", **environment):
        return subprocess.run(["bash", str(self.root / "scripts" / name)], cwd=self.root,
                              env=self.environment | environment, capture_output=True, text=True)

    def events(self):
        return [json.loads(line) for line in self.log.read_text().splitlines()] if self.log.exists() else []

    def artifacts(self):
        output = self.root / "dari-desktop-app/build/release"
        return list(output.glob("*"))

    def restore_entitlements(self):
        packaging = self.root / "dari-desktop-app/packaging"
        packaging.mkdir(parents=True, exist_ok=True)
        shutil.copy2(ROOT / "dari-desktop-app/packaging/macos-entitlements.plist", packaging)

    def test_final_hash_and_app_ticket_for_both_architectures(self):
        for arch, suffix in (("arm64", "arm64"), ("x86_64", "x64")):
            with self.subTest(arch=arch):
                # Each architecture needs a fresh bundle as a real runner would have.
                shutil.rmtree(self.root / "dari-desktop-app", ignore_errors=True)
                self.restore_entitlements()
                result = self.run_script(TEST_ARCH=arch)
                self.assertEqual(result.returncode, 0, result.stderr)
                dmg = self.root / f"dari-desktop-app/build/release/Dari-2.1.1-{suffix}.dmg"
                self.assertEqual(dmg.read_bytes(), b"disk-image-signed-stapled")
                expected_hash = hashlib.sha256(dmg.read_bytes()).hexdigest()
                self.assertEqual(Path(str(dmg) + ".sha256").read_text().split()[0], expected_hash)
                events = self.events()
                submits = [event for event in events if event[:3] == ["xcrun", "notarytool", "submit"]]
                self.assertTrue(submits[-2][3].endswith("Dari.zip"))
                self.assertTrue(submits[-1][3].endswith(".dmg"))
                self.assertTrue(any(event[:2] == ["smoke-test-desktop-app.sh", "--skip-build"] for event in events))
                ticket = self.root / "dari-desktop-app/build/compose/binaries/main-release/app/Dari.app/Contents/stapled-ticket"
                self.assertTrue(ticket.exists())
                self.assertFalse(any(event[0] == "codesign" and "--sign" in event and "-" in event for event in events))
                for kind in ("app", "dmg"):
                    last_events = events[events.index(submits[-2]):]
                    target = str(ticket.parent.parent.relative_to(self.root)) if kind == "app" else submits[-1][3]
                    staple = last_events.index(["xcrun", "stapler", "staple", target])
                    validate = last_events.index(["xcrun", "stapler", "validate", target])
                    assessment = next(index for index, event in enumerate(last_events)
                                      if event[0] == "spctl" and event[-1] == target)
                    self.assertLess(staple, validate)
                    self.assertLess(validate, assessment)

    def test_invalid_or_failed_notarization_never_publishes_artifacts(self):
        for setting in ("REJECT_NOTARY", "FAIL_NOTARY"):
            for kind in ("app", "dmg"):
                with self.subTest(setting=setting, kind=kind):
                    shutil.rmtree(self.root / "dari-desktop-app", ignore_errors=True)
                    self.restore_entitlements()
                    result = self.run_script(**{setting: kind})
                    self.assertNotEqual(result.returncode, 0)
                    self.assertEqual(self.artifacts(), [])

    def test_signature_entitlement_staple_and_version_failures(self):
        for setting in ("FAIL_SIGNATURE", "WRONG_TEAM", "ADHOC_SIGNATURE", "MISSING_JIT", "FAIL_STAPLE"):
            with self.subTest(setting=setting):
                shutil.rmtree(self.root / "dari-desktop-app", ignore_errors=True)
                self.restore_entitlements()
                result = self.run_script(**{setting: "1"})
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(self.artifacts(), [])
        shutil.rmtree(self.root / "dari-desktop-app", ignore_errors=True)
        self.restore_entitlements()
        self.assertNotEqual(self.run_script(TEST_APP_VERSION="2.1.0").returncode, 0)
        self.assertEqual(self.artifacts(), [])

    def test_transient_staple_failures_are_retried(self):
        result = self.run_script(FLAKY_STAPLE="2")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("retrying", result.stderr)
        staples = [event for event in self.events() if event[:3] == ["xcrun", "stapler", "staple"]]
        # Two failed attempts on the app, then one success each for the app and the DMG.
        self.assertEqual(len(staples), 4)
        self.assertTrue(staples[-1][-1].endswith(".dmg"))
        self.assertEqual(len([path for path in self.artifacts() if path.suffix == ".dmg"]), 1)

    def test_staple_validation_and_gatekeeper_failures_never_publish(self):
        for setting in ("FAIL_VALIDATE", "FAIL_ASSESS"):
            for kind in ("app", "dmg"):
                with self.subTest(setting=setting, kind=kind):
                    shutil.rmtree(self.root / "dari-desktop-app", ignore_errors=True)
                    self.restore_entitlements()
                    self.assertNotEqual(self.run_script(**{setting: kind}).returncode, 0)
                    self.assertEqual(self.artifacts(), [])

    def test_entitlements_preserve_jdk17_non_app_store_defaults(self):
        expected = {"com.apple.security.cs." + key for key in (
            "allow-jit", "allow-unsigned-executable-memory", "disable-library-validation",
            "allow-dyld-environment-variables", "debugger",
        )} | {"com.apple.security.device.audio-input"}
        with (ROOT / "dari-desktop-app/packaging/macos-entitlements.plist").open("rb") as source:
            entitlements = plistlib.load(source)
        self.assertEqual(set(entitlements), expected)
        self.assertTrue(all(value is True for value in entitlements.values()))

    def test_missing_credentials_stop_before_build_or_apple_calls(self):
        self.assertNotEqual(self.run_script(MACOS_SIGNING_IDENTITY="").returncode, 0)
        self.assertTrue(all(event[0] == "uname" for event in self.events()))

    def test_missing_dmgbuild_stops_before_build(self):
        self.assertNotEqual(self.run_script(DMGBUILD="missing-dmgbuild").returncode, 0)
        self.assertTrue(all(event[0] == "uname" for event in self.events()))

    def test_setup_selects_identity_and_removes_credential_files(self):
        result = self.run_script("setup-macos-signing.sh")
        self.assertEqual(result.returncode, 0, result.stderr)
        exported = (self.root / "github-env").read_text()
        self.assertIn("MACOS_SIGNING_IDENTITY=Developer ID Application: Example", exported)
        self.assertIn("MACOS_NOTARY_PROFILE=dari-release", exported)
        for secret in ("dummy-p12-password", "dummy-app-password", "dummy@example.invalid"):
            self.assertNotIn(secret, result.stdout + result.stderr + exported)
        self.assertFalse((self.root / "dari-signing.p12").exists())
        self.assertFalse((self.root / "dari-notary-auth.log").exists())
        self.assertIn(["security", "list-keychains", "-d", "user", "-s", str(self.root / "dari-signing.keychain-db"),
                       "/Users/runner/Library/Keychains/login.keychain-db"], self.events())

    def test_setup_rejects_wrong_or_ambiguous_certificate(self):
        for environment in ({"WRONG_CERTIFICATE": "1"}, {"IDENTITY_COUNT": "2"}):
            with self.subTest(environment=environment):
                (self.root / "dari-signing.keychain-db").unlink(missing_ok=True)
                result = self.run_script("setup-macos-signing.sh", **environment)
                self.assertNotEqual(result.returncode, 0)
                self.assertFalse((self.root / "dari-signing.p12").exists())

    def test_auth_failure_prints_redacted_auth_diagnostics(self):
        result = self.run_script("setup-macos-signing.sh", FAIL_AUTH="1")
        output = result.stdout + result.stderr
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("HTTP status code: 401 for <APPLE_ID> <APPLE_APP_SPECIFIC_PASSWORD>", output)
        self.assertNotIn("dummy@example.invalid", output)
        self.assertNotIn("dummy-app-password", output)
        self.assertFalse((self.root / "dari-notary-auth.log").exists())


if __name__ == "__main__":
    unittest.main()

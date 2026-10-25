"""Regression checks added during review. GitHub is simulated; no APK is built here."""
import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from release_validation import device_evidence, validate_assets
from device_smoke import passed_tests, collect_native_screens, run_instrumentation, app_node, navigate_page, PACKAGE
import xml.etree.ElementTree as ET
import test_release
from test_release import FakeGitHub
import prepare_signing

class ReleaseReviewTests(unittest.TestCase):
    setUp = test_release.PublishTests.setUp
    rehash = test_release.PublishTests.rehash
    run_publish = test_release.PublishTests.run_publish
    def test_existing_release_at_another_commit_is_not_green(self):
        api = FakeGitHub(existing={"draft": False}, tag={"object": {"type": "commit", "sha": "f"*40}})
        with self.assertRaises(RuntimeError): self.run_publish(api)
        self.assertEqual(api.calls, [])

    def test_existing_release_without_tag_is_rejected(self):
        with self.assertRaises(RuntimeError): self.run_publish(FakeGitHub(existing={"draft": False}))

    def test_tampered_apk_rejected_before_network(self):
        (self.dist/self.info["apk"]).write_bytes(b"changed")
        api = FakeGitHub()
        with self.assertRaises(ValueError): self.run_publish(api)
        self.assertEqual(api.calls, [])

    def test_tampered_certificate_rejected(self):
        (self.dist/"signing-certificate.txt").write_bytes(b"changed")
        with self.assertRaises(ValueError): self.run_publish(FakeGitHub())

    def test_duplicate_checksum_rejected(self):
        path = self.dist/"SHA256SUMS"
        path.write_text(path.read_text() + path.read_text().splitlines()[0] + "\n")
        with self.assertRaises(ValueError): self.run_publish(FakeGitHub())

    def test_missing_checksum_rejected(self):
        path = self.dist/"SHA256SUMS"
        path.write_text("\n".join(path.read_text().splitlines()[1:]) + "\n")
        with self.assertRaises(ValueError): self.run_publish(FakeGitHub())

    def test_private_filename_cannot_be_whitelisted_via_metadata(self):
        self.info["apk"] = "release.p12"
        (self.dist/"release.p12").write_bytes(b"not a key")
        self.rehash()
        with self.assertRaises(ValueError): self.run_publish(FakeGitHub())

    def test_symlink_asset_rejected(self):
        path = self.dist/"mapping.txt"
        path.symlink_to(self.dist/"signing-certificate.txt")
        with self.assertRaises(ValueError): self.run_publish(FakeGitHub())

    def test_subdirectory_rejected(self):
        (self.dist/"nested").mkdir()
        with self.assertRaises(ValueError): self.run_publish(FakeGitHub())

    def test_wrong_package_rejected(self):
        self.info["applicationId"] = "dev.vitreo.launcher.debug"
        self.rehash()
        with self.assertRaises(ValueError): self.run_publish(FakeGitHub())

    def test_preview_cannot_take_release_tag(self):
        old = self.dist/self.info["apk"]
        self.info.update(buildType="preview", applicationId="dev.vitreo.launcher.preview", apk="vitreo-0.1.0-preview.apk")
        old.rename(self.dist/self.info["apk"])
        self.rehash()
        with self.assertRaises(ValueError): self.run_publish(FakeGitHub())

    def test_failed_device_tests_block_release(self):
        self.info["deviceTests"][0]["status"] = "failed"
        self.rehash()
        with self.assertRaises(ValueError): self.run_publish(FakeGitHub())

    def test_device_tests_for_old_commit_block_release(self):
        self.info["deviceTests"][0]["commit"] = "f"*40
        self.rehash()
        with self.assertRaises(ValueError): self.run_publish(FakeGitHub())

    def test_unexecuted_optimized_smoke_blocks_release(self):
        self.info["deviceTests"][1]["optimizedPreviewStarted"] = False
        self.rehash()
        with self.assertRaises(ValueError): self.run_publish(FakeGitHub())

    def test_missing_api_blocks_release(self):
        self.info["deviceTests"].pop()
        self.rehash()
        with self.assertRaises(ValueError): self.run_publish(FakeGitHub())

    def test_bad_remote_digest_leaves_draft(self):
        api = FakeGitHub()
        original = api.request
        def request(method, path, body=None, content_type=None):
            result = original(method, path, body, content_type)
            if "uploads.github.com" in path:
                result["digest"] = "sha256:" + "0"*64
            return result
        api.request = request
        with self.assertRaises(RuntimeError): self.run_publish(api)
        self.assertFalse(any(c[0] == "PATCH" for c in api.calls))

class DeviceEvidenceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.commit = "a"*40
    def write(self, api, **overrides):
        path = self.root/str(api); path.mkdir(exist_ok=True)
        value = dict(api=api, commit=self.commit, status="passed", debugTestsPassed=5, optimizedPreviewStarted=True,
                     debugTestPhases={"application": 1, "systemHome": 2, "systemLock": 2})
        value.update(overrides)
        (path/"smoke-result.json").write_text(json.dumps(value))
    def test_matching_matrix(self):
        self.write(31); self.write(35)
        self.assertEqual([v["api"] for v in device_evidence(self.root,self.commit)],[31,35])
    def test_empty_directory_is_not_passed(self):
        with self.assertRaises(ValueError): device_evidence(self.root,self.commit)
    def test_missing_api_is_not_passed(self):
        self.write(31)
        with self.assertRaises(ValueError): device_evidence(self.root,self.commit)
    def test_wrong_source_rejected(self):
        self.write(31); self.write(35,commit="b"*40)
        with self.assertRaises(ValueError): device_evidence(self.root,self.commit)
    def test_zero_tests_rejected(self):
        self.write(31); self.write(35,debugTestsPassed=0)
        with self.assertRaises(ValueError): device_evidence(self.root,self.commit)
    def test_failed_test_rejected(self):
        self.write(31,status="failed"); self.write(35)
        with self.assertRaises(ValueError): device_evidence(self.root,self.commit)
    def test_no_preview_launch_rejected(self):
        self.write(31); self.write(35,optimizedPreviewStarted=False)
        with self.assertRaises(ValueError): device_evidence(self.root,self.commit)
    def test_duplicate_api_rejected(self):
        self.write(31); self.write(35)
        duplicate=self.root/"duplicate"; duplicate.mkdir()
        (duplicate/"smoke-result.json").write_text((self.root/"31/smoke-result.json").read_text())
        with self.assertRaises(ValueError): device_evidence(self.root,self.commit)
    def test_secure_keyguard_phase_and_exact_accounting_are_required(self):
        for phases in ({}, {"application": 3, "systemHome": 2},
                       {"application": 2, "systemHome": 2, "systemLock": 1},
                       {"application": 2, "systemHome": 2, "systemLock": 2},
                       {"application": True, "systemHome": 2, "systemLock": 2}):
            with self.subTest(phases=phases):
                self.write(31); self.write(35, debugTestPhases=phases)
                with self.assertRaises(ValueError): device_evidence(self.root, self.commit)

    def test_junit_reports_required(self):
        with self.assertRaises(RuntimeError): passed_tests(self.root)
    def test_junit_failures_block_success(self):
        (self.root/"TEST-example.xml").write_text('<testsuite tests="4" failures="1" errors="0" skipped="0"/>')
        with self.assertRaises(RuntimeError): passed_tests(self.root)
    def test_junit_all_skipped_is_not_passed(self):
        (self.root/"TEST-example.xml").write_text('<testsuite tests="4" failures="0" errors="0" skipped="4"/>')
        with self.assertRaises(RuntimeError): passed_tests(self.root)
    def test_junit_counts_exclude_skips(self):
        (self.root/"TEST-example.xml").write_text('<testsuite tests="4" failures="0" errors="0" skipped="1"/>')
        self.assertEqual(passed_tests(self.root),3)

class NativeScreenshotTests(unittest.TestCase):
    def test_instrumentation_keeps_apks_and_output_until_collection(self):
        from test_instrumentation_results import transcript
        from types import SimpleNamespace
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for relative in ("app/build/outputs/apk/debug/app.apk", "app/build/outputs/apk/androidTest/debug/test.apk"):
                file = root / relative; file.parent.mkdir(parents=True); file.write_bytes(b"fixture")
            with patch("device_smoke.ROOT", root), patch("device_smoke.adb") as device, \
                patch("device_smoke.prepare_emulator_home"), \
                patch("device_smoke.subprocess.Popen") as logging, \
                patch("device_smoke.subprocess.run", return_value=SimpleNamespace(returncode=0, stdout=transcript())) as run:
                self.assertEqual(run_instrumentation(), 2)
            self.assertTrue((root / "app/build/outputs/androidTest-results/connected/TEST-instrumentation.xml").is_file())
            self.assertFalse(any("uninstall" in call.args for call in device.call_args_list))
            self.assertIn("instrument", run.call_args.args[0])
            self.assertIn("notClass", run.call_args.args[0])
            self.assertFalse(any("gradlew" in part for part in run.call_args.args[0]))

    def test_system_home_phase_keeps_earlier_reports_and_requires_all_three_tests(self):
        from test_instrumentation_results import transcript
        from types import SimpleNamespace
        from device_smoke import SYSTEM_HOME_TEST
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            earlier = root / "app/build/outputs/androidTest-results/connected/TEST-instrumentation.xml"
            earlier.parent.mkdir(parents=True); earlier.write_text("existing application results")
            output = transcript((0,0,0)).replace("example.Tests", SYSTEM_HOME_TEST)
            with patch("device_smoke.ROOT", root), patch("device_smoke.adb") as device, \
                patch("device_smoke.subprocess.Popen"), \
                patch("device_smoke.subprocess.run", return_value=SimpleNamespace(returncode=0, stdout=output)) as run:
                self.assertEqual(run_instrumentation(system_home=True), 3)
            self.assertEqual(earlier.read_text(), "existing application results")
            self.assertTrue(earlier.with_name("TEST-system-home.xml").is_file())
            self.assertIn("class", run.call_args.args[0])
            self.assertFalse(any(call.args[0] in ("install", "uninstall") for call in device.call_args_list))

    def test_system_home_phase_rejects_wrong_filter_or_missing_test(self):
        from test_instrumentation_results import transcript
        from types import SimpleNamespace
        from device_smoke import SYSTEM_HOME_TEST
        for output in (transcript(), transcript((0,)).replace("example.Tests", SYSTEM_HOME_TEST), transcript((0,0)).replace("example.Tests", SYSTEM_HOME_TEST)):
            with self.subTest(output=output), tempfile.TemporaryDirectory() as directory:
                with patch("device_smoke.ROOT", Path(directory)), patch("device_smoke.adb"), \
                    patch("device_smoke.subprocess.Popen"), \
                    patch("device_smoke.subprocess.run", return_value=SimpleNamespace(returncode=0, stdout=output)):
                    with self.assertRaises(RuntimeError): run_instrumentation(system_home=True)

    def test_secure_keyguard_phase_is_complete_and_keeps_previous_reports(self):
        from test_instrumentation_results import transcript
        from types import SimpleNamespace
        from device_smoke import SYSTEM_LOCK_TEST
        for output, passes in ((transcript().replace("example.Tests", SYSTEM_LOCK_TEST), True),
                               (transcript(), False), (transcript((0,)).replace("example.Tests", SYSTEM_LOCK_TEST), False)):
            with self.subTest(passes=passes), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                previous = root / "app/build/outputs/androidTest-results/connected/TEST-system-home.xml"
                previous.parent.mkdir(parents=True); previous.write_text("prior evidence")
                with patch("device_smoke.ROOT", root), patch("device_smoke.adb") as device, \
                    patch("device_smoke.subprocess.Popen"), \
                    patch("device_smoke.subprocess.run", return_value=SimpleNamespace(returncode=0, stdout=output)) as run:
                    if passes: self.assertEqual(run_instrumentation(system_lock=True), 2)
                    else:
                        with self.assertRaises(RuntimeError): run_instrumentation(system_lock=True)
                self.assertEqual(previous.read_text(), "prior evidence")
                self.assertIn(SYSTEM_LOCK_TEST, run.call_args.args[0])
                self.assertFalse(any(call.args[0] in ("install", "uninstall") for call in device.call_args_list))

    def test_file_transfer_preserves_binary_png_header(self):
        data = bytes([137,80,78,71,13,10,26,10,0,255,13,10])
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            def pull(*args):
                self.assertEqual(args[:2], ("pull", "/sdcard/Android/data/dev.vitreo.launcher.debug/files/review/category-light.png"))
                Path(args[2]).write_bytes(data)
                return "1 file pulled"
            with patch("device_smoke.adb", side_effect=pull):
                collect_native_screens(root, ["category-light"])
            self.assertEqual((root / "native-category-light.png").read_bytes(), data)

    def test_invalid_file_keeps_diagnostics_and_blocks_success(self):
        with tempfile.TemporaryDirectory() as directory:
            def pull(*args):
                Path(args[2]).write_bytes(b"not a PNG")
            with patch("device_smoke.adb", side_effect=pull):
                with self.assertRaisesRegex(RuntimeError, "Invalid native screenshot category-light"):
                    collect_native_screens(Path(directory), ["category-light"])

class OptimizedNavigationTests(unittest.TestCase):
    def test_app_name_in_editable_search_is_not_the_app(self):
        root = ET.fromstring(f'''<hierarchy>
            <node package="{PACKAGE}" class="android.widget.EditText" text="Settings" clickable="true" long-clickable="true" bounds="[10,20][200,60]" />
            <node package="{PACKAGE}" class="android.view.View" clickable="true" long-clickable="true" bounds="[10,100][80,200]">
                <node text="Settings" class="android.widget.TextView" />
            </node></hierarchy>''')
        self.assertEqual(app_node(root, "Settings").get("bounds"), "[10,100][80,200]")

    def test_editable_search_alone_does_not_prove_app_result(self):
        root = ET.fromstring(f'<hierarchy><node package="{PACKAGE}" class="android.widget.EditText" text="Settings" clickable="true" long-clickable="true" /></hierarchy>')
        with self.assertRaisesRegex(RuntimeError, "missing from optimized results"):
            app_node(root, "Settings")

    def test_navigation_observes_the_page_before_advancing(self):
        home = ET.fromstring(f'<hierarchy><node package="{PACKAGE}" text="Search" /></hierarchy>')
        today = ET.fromstring(f'<hierarchy><node package="{PACKAGE}" text="Today" /></hierarchy>')
        with patch("device_smoke.read_ui", side_effect=[home, home, today]), patch("device_smoke.capture_screen", return_value=today) as capture:
            from unittest.mock import Mock
            swipe = Mock()
            self.assertIs(navigate_page(Path("unused"), "today", "Today", swipe, 3), today)
            self.assertEqual(swipe.call_count, 2)
            capture.assert_called_once_with(Path("unused"), "today", "Today")

class StrictSigningTests(unittest.TestCase):
    def test_release_never_silently_falls_back_to_preview(self):
        with patch.dict(os.environ,{},clear=True), patch.object(sys,"argv",["prepare_signing.py","--require-private"]):
            with self.assertRaises(ValueError): prepare_signing.main()

    def test_signing_preflight_without_secrets_explains_configuration(self):
        with patch.dict(os.environ,{},clear=True), patch.object(sys,"argv",["prepare_signing.py","--require-private","--check-only"]):
            with self.assertRaisesRegex(ValueError, "Settings > Secrets and variables > Actions"):
                prepare_signing.main()

    def test_signing_preflight_needs_no_runner_paths_and_writes_no_key(self):
        environment = dict(ANDROID_KEYSTORE_BASE64="YQ==", ANDROID_KEYSTORE_PASSWORD="private-store-password",
            ANDROID_KEY_ALIAS="vitreo", ANDROID_KEY_PASSWORD="private-key-password")
        with patch.dict(os.environ,environment,clear=True), patch.object(sys,"argv",["prepare_signing.py","--require-private","--check-only"]):
            # RUNNER_TEMP/GITHUB_ENV/GITHUB_OUTPUT are deliberately absent. Presence
            # checks must work without creating a keystore or exporting credentials.
            prepare_signing.main()

if __name__ == "__main__":
    unittest.main()

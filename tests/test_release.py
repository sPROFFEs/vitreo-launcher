"""Offline tests only: no requests to GitHub and no release publication."""
import base64
import hashlib
import json
import os
from pathlib import Path
import shutil
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from release_common import SIGNING_SECRETS, read_version, signing_mode, validate_ref, validate_repository
from github_release import GitHub, ApiError, SafeRedirect, publish, release_body
import prepare_signing
import gradle_bootstrap

class ConfigurationTests(unittest.TestCase):
    def test_current_version(self): self.assertEqual(read_version(), ("0.1.0", 100)) if read_version()[0] == "0.1.0" else self.assertGreater(read_version()[1], 100)
    def test_preview_without_secrets(self): self.assertEqual(signing_mode({}), "preview")
    def test_private_with_complete_secrets(self): self.assertEqual(signing_mode(dict.fromkeys(SIGNING_SECRETS, "secret")), "release")
    def test_partial_secrets_fail(self):
        for name in SIGNING_SECRETS:
            with self.subTest(name=name), self.assertRaises(ValueError): signing_mode({name: "secret"})
    def test_empty_secrets_are_absent(self): self.assertEqual(signing_mode(dict.fromkeys(SIGNING_SECRETS, "")), "preview")
    def test_allowed_repository(self): self.assertEqual(validate_repository("alice/vitreo-launcher"), "alice/vitreo-launcher")
    def test_invalid_repository(self):
        for repo in ("x", "https://github.com/a/b", "a/b/c", "a;echo/b", "a/../b", "-bad/repo"):
            with self.subTest(repo=repo), self.assertRaises(ValueError): validate_repository(repo)
    def test_main_and_matching_tag(self): validate_ref("refs/heads/main", "0.1.0"); validate_ref("refs/tags/v0.1.0", "0.1.0")
    def test_wrong_ref_rejected(self):
        for ref in ("refs/heads/feature", "refs/tags/v0.2.0", "refs/pull/1/merge"):
            with self.subTest(ref=ref), self.assertRaises(ValueError): validate_ref(ref, "0.1.0")
    def test_invalid_version_code(self):
        with tempfile.TemporaryDirectory() as temp:
            file = Path(temp) / "version.properties"; file.write_text("VERSION_NAME=0.1.0\nVERSION_CODE=101\n")
            with self.assertRaises(ValueError): read_version(file)
    def test_semver_prerelease_refused(self):
        with tempfile.TemporaryDirectory() as temp:
            file = Path(temp) / "version.properties"; file.write_text("VERSION_NAME=0.1.0-beta\nVERSION_CODE=100\n")
            with self.assertRaises(ValueError): read_version(file)
    def test_checksum_rejects_untrusted_archive(self):
        with tempfile.TemporaryDirectory() as temp:
            file = Path(temp) / "gradle.zip"; file.write_bytes(b"not gradle")
            with self.assertRaises(RuntimeError): gradle_bootstrap.validate_archive(file, Path(temp))

class HttpTests(unittest.TestCase):
    def test_no_token_refused(self):
        with self.assertRaises(ValueError): GitHub("")
    def test_token_not_sent_to_external_host(self):
        with self.assertRaises(ValueError): GitHub("fake").request("POST", "https://untrusted.example/upload", b"payload")
    def test_authenticated_redirect_rejected(self):
        with self.assertRaises(ValueError):
            SafeRedirect().redirect_request(None, None, 302, "redirect", {}, "https://untrusted.example/steal")
    def test_optional_404(self):
        api = GitHub("fake")
        with patch.object(api, "request", side_effect=ApiError(404, "missing")): self.assertIsNone(api.optional("/test"))
    def test_optional_403_not_treated_as_missing(self):
        api = GitHub("fake")
        with patch.object(api, "request", side_effect=ApiError(403, "forbidden")), self.assertRaises(ApiError): api.optional("/test")

class FakeGitHub:
    def __init__(self, existing=None, tag=None, fail_upload=False):
        self.existing, self.tag, self.fail_upload = existing, tag, fail_upload
        self.calls = []
    def optional(self, path):
        return self.existing if "/releases/tags/" in path else self.tag
    def request(self, method, path, body=None, content_type=None):
        self.calls.append((method, path, body))
        if method == "POST" and path.endswith("/releases"):
            return {"id": 11, "upload_url": "https://uploads.github.com/repos/alice/vitreo/releases/11/assets{?name,label}"}
        if method == "POST" and "uploads.github.com" in path:
            if self.fail_upload: raise ApiError(500, "simulated upload failure")
            return {"state": "uploaded", "size": len(body), "digest": "sha256:" + hashlib.sha256(body).hexdigest()}
        if method == "PATCH": return {"html_url": "https://github.com/alice/vitreo/releases/tag/v0.1.0"}
        raise AssertionError((method,path))

class PublishTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.dist = Path(self.temp.name)
        environment = patch.dict(os.environ)
        environment.start(); self.addCleanup(environment.stop)
        os.environ.pop("GITHUB_STEP_SUMMARY", None)
        self.commit = "a" * 40
        self.info = {"versionName":"0.1.0", "versionCode":100, "commit":self.commit, "buildType":"release",
            "applicationId":"dev.vitreo.launcher", "apk":"vitreo-0.1.0-release.apk", "apkSha256":"b"*64, "signingCertificateSha256":"c"*64}
        self.info["deviceTests"] = [{"api": api, "commit": self.commit, "status": "passed",
            "debugTestsPassed": 5, "optimizedPreviewStarted": True,
            "debugTestPhases": {"application": 1, "systemHome": 2, "systemLock": 2}} for api in (31, 35)]
        for name in (self.info["apk"], "signing-certificate.txt"):
            (self.dist/name).write_text("fake fixture, not an APK")
        self.info["apkSha256"] = hashlib.sha256((self.dist/self.info["apk"]).read_bytes()).hexdigest()
        self.rehash()
    def rehash(self):
        (self.dist / "build-info.json").write_text(json.dumps(self.info))
        files = sorted(p for p in self.dist.iterdir() if p.is_file() and p.name != "SHA256SUMS")
        (self.dist / "SHA256SUMS").write_text("".join(f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n" for p in files))
    def run_publish(self, api):
        with patch("builtins.print"):
            return publish(api, "alice/vitreo", "0.1.0", self.commit, self.dist)
    def test_existing_release_never_overwritten(self):
        api=FakeGitHub(existing={"draft":False}, tag={"object":{"type":"commit","sha":self.commit}}); self.assertEqual(self.run_publish(api),"unchanged"); self.assertEqual(api.calls,[])
    def test_existing_draft_never_overwritten(self):
        api=FakeGitHub(existing={"draft":True})
        with self.assertRaises((RuntimeError, ValueError)): self.run_publish(api)
        self.assertEqual(api.calls,[])
    def test_existing_prerelease_promoted_without_touching_assets(self):
        api = FakeGitHub(existing={"id": 11, "draft": False, "prerelease": True},
            tag={"object": {"type": "commit", "sha": self.commit}})
        self.assertEqual(self.run_publish(api), "promoted")
        self.assertEqual(api.calls, [("PATCH", "/repos/alice/vitreo/releases/11",
            {"prerelease": False, "make_latest": "legacy"})])
    def test_prerelease_at_wrong_commit_cannot_be_promoted(self):
        api = FakeGitHub(existing={"id": 11, "draft": False, "prerelease": True},
            tag={"object": {"type": "commit", "sha": "f" * 40}})
        with self.assertRaises(RuntimeError): self.run_publish(api)
        self.assertEqual(api.calls, [])
    def test_wrong_tag_commit_rejected(self):
        api=FakeGitHub(tag={"object":{"type":"commit","sha":"f"*40}})
        with self.assertRaises((RuntimeError, ValueError)): self.run_publish(api)
        self.assertEqual(api.calls,[])
    def test_matching_tag_accepted(self):
        api=FakeGitHub(tag={"object":{"type":"commit","sha":self.commit}})
        self.assertEqual(self.run_publish(api),"published")
    def test_happy_path_publishes_only_after_assets(self):
        api=FakeGitHub(); self.assertEqual(self.run_publish(api),"published")
        self.assertTrue(api.calls[0][2]["draft"])
        self.assertFalse(api.calls[0][2]["prerelease"])
        self.assertEqual(len([c for c in api.calls if "uploads.github.com" in c[1]]), 4)
        self.assertEqual(api.calls[-1][0],"PATCH"); self.assertFalse(api.calls[-1][2]["draft"])
        self.assertFalse(api.calls[-1][2]["prerelease"])
        self.assertEqual(api.calls[-1][2]["make_latest"], "legacy")
    def test_release_notes_do_not_label_numeric_versions_experimental(self):
        self.assertIn("# Vitreo 0.1.0\n", release_body(self.info))
        self.assertNotIn("experimental", release_body(self.info))
    def test_upload_failure_does_not_publish(self):
        api=FakeGitHub(fail_upload=True)
        with self.assertRaises(ApiError): self.run_publish(api)
        self.assertFalse(any(c[0]=="PATCH" for c in api.calls))
    def test_mismatched_metadata_rejected(self):
        self.info["commit"]="d"*40; (self.dist/"build-info.json").write_text(json.dumps(self.info))
        api=FakeGitHub()
        with self.assertRaises((RuntimeError, ValueError)): self.run_publish(api)
        self.assertEqual(api.calls,[])
    def test_private_asset_refused(self):
        (self.dist/"release.p12").write_text("fake key")
        api=FakeGitHub()
        with self.assertRaises((RuntimeError, ValueError)): self.run_publish(api)
        self.assertEqual(api.calls,[])

class SigningTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)
        self.env={"RUNNER_TEMP":str(self.root),"GITHUB_ENV":str(self.root/"env"),"GITHUB_OUTPUT":str(self.root/"out")}
    def test_preview_generates_no_key(self):
        with patch.dict(os.environ,self.env,clear=True),patch.object(sys,"argv",["prepare_signing.py"]): prepare_signing.main()
        self.assertEqual(list(self.root.glob("*.p12")),[])
        self.assertIn("mode=preview",(self.root/"out").read_text())
    def test_private_materialized_only_in_runner_temp(self):
        values=dict(self.env,ANDROID_KEYSTORE_BASE64=base64.b64encode(b"fixture-not-a-real-key"*10).decode(),
            ANDROID_KEYSTORE_PASSWORD="password",ANDROID_KEY_ALIAS="vitreo",ANDROID_KEY_PASSWORD="password")
        with patch.dict(os.environ,values,clear=True),patch.object(sys,"argv",["prepare_signing.py"]): prepare_signing.main()
        keys=list(self.root.glob("*.p12")); self.assertEqual(len(keys),1)
        self.assertEqual(keys[0].stat().st_mode & 0o777,0o600)
        self.assertIn("mode=release",(self.root/"out").read_text())
    def test_invalid_base64_rejected(self):
        values=dict(self.env,**dict.fromkeys(SIGNING_SECRETS,"!!!"))
        with patch.dict(os.environ,values,clear=True),patch.object(sys,"argv",["prepare_signing.py"]),self.assertRaises(ValueError): prepare_signing.main()
    def test_newline_secret_rejected(self):
        values=dict(self.env,ANDROID_KEYSTORE_BASE64=base64.b64encode(b"x"*100).decode(),ANDROID_KEYSTORE_PASSWORD="bad\nvalue",
            ANDROID_KEY_ALIAS="vitreo",ANDROID_KEY_PASSWORD="password")
        with patch.dict(os.environ,values,clear=True),patch.object(sys,"argv",["prepare_signing.py"]),self.assertRaises(ValueError): prepare_signing.main()
    def test_cleanup_never_deletes_unrelated_file(self):
        key=self.root/"other.p12"; key.write_bytes(b"fake")
        with patch.dict(os.environ,dict(self.env,VITREO_STORE_FILE=str(key)),clear=True): prepare_signing.clean()
        self.assertTrue(key.exists())
    def test_cleanup_deletes_own_file(self):
        key=self.root/"vitreo-signing-test.p12"; key.write_bytes(b"fake")
        with patch.dict(os.environ,dict(self.env,VITREO_STORE_FILE=str(key)),clear=True): prepare_signing.clean()
        self.assertFalse(key.exists())

if __name__=="__main__": unittest.main()

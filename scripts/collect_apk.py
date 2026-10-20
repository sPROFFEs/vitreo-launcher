#!/usr/bin/env python3
"""Verify identity, version, permissions and signature before staging release assets."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
from datetime import datetime, timezone
from release_common import ROOT, read_version
from release_validation import device_evidence, validate_assets
from manifest_policy import validate_apk_lock_activity, PERMISSIONS, validate_apk_services

def command(*args: str) -> str:
    return subprocess.check_output(args, text=True, stderr=subprocess.STDOUT)

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("preview", "release"))
    parser.add_argument("--device-reports", type=Path)
    args = parser.parse_args()
    version, code = read_version()
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ["ANDROID_SDK_ROOT"])
    tools = sdk / "build-tools" / "35.0.0"
    candidates = list((ROOT / "app" / "build" / "outputs" / "apk" / args.mode).glob("*.apk"))
    if len(candidates) != 1:
        raise RuntimeError(f"Expected exactly one {args.mode} APK, found {len(candidates)}.")
    apk = candidates[0]
    expected_id = "dev.vitreo.launcher" + (".preview" if args.mode == "preview" else "")
    badging = command(str(tools / "aapt"), "dump", "badging", str(apk))
    match = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
    if not match or match.groups() != (expected_id, str(code), version):
        raise RuntimeError("APK identity/version does not match the intended release.")
    if "application-debuggable" in badging:
        raise RuntimeError("A debuggable APK cannot be published as release/preview.")
    if "sdkVersion:'31'" not in badging or "targetSdkVersion:'35'" not in badging:
        raise RuntimeError("Unexpected minimum or target SDK in the APK.")
    permissions = command(str(tools / "aapt"), "dump", "permissions", str(apk))
    requested = re.findall(r"uses-permission(?:-[^:]+)?: name='([^']+)'", permissions)
    allowed = PERMISSIONS | {expected_id + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"}
    if set(requested) - allowed:
        raise RuntimeError(f"Unexpected APK permissions: {sorted(set(requested) - allowed)}")
    manifest_tree = command(str(tools / "aapt"), "dump", "xmltree", str(apk), "AndroidManifest.xml")
    validate_apk_services(manifest_tree)
    validate_apk_lock_activity(manifest_tree, expected_id)
    certificate = command(str(tools / "apksigner"), "verify", "--verbose", "--print-certs", str(apk))
    fingerprint = re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)", certificate)
    if not fingerprint:
        raise RuntimeError("Signed APK verification did not yield a certificate fingerprint.")
    revision = command("git", "-C", str(ROOT), "rev-parse", "HEAD").strip()
    evidence = device_evidence(args.device_reports, revision) if args.device_reports else []
    if args.mode == "release":
        if not evidence:
            raise RuntimeError("Release collection requires executed device test evidence.")
        # Match the APK signer to the private key selected by this job, not just any valid key.
        key = os.environ.get("VITREO_STORE_FILE")
        alias = os.environ.get("VITREO_KEY_ALIAS")
        if not key or not alias:
            raise RuntimeError("Private signing environment missing during signer verification.")
        der = subprocess.check_output(["keytool", "-exportcert", "-keystore", key,
            "-storepass:env", "VITREO_STORE_PASSWORD", "-alias", alias], stderr=subprocess.PIPE)
        if hashlib.sha256(der).hexdigest() != fingerprint.group(1).lower():
            raise RuntimeError("APK signer does not match the configured release key.")
    output = ROOT / "dist"
    output.mkdir(exist_ok=True)
    if any(output.iterdir()):
        raise RuntimeError("dist is not empty; refusing to include stale release assets.")
    name = f"vitreo-{version}-{args.mode}.apk"
    shutil.copy2(apk, output / name)
    (output / "signing-certificate.txt").write_text(certificate)
    info = {"app": "Vitreo", "versionName": version, "versionCode": code, "applicationId": expected_id,
        "buildType": args.mode, "commit": revision, "builtAtUtc": datetime.now(timezone.utc).isoformat(),
        "minSdk": 31, "targetSdk": 35, "apk": name, "apkSha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
        "signingCertificateSha256": fingerprint.group(1).lower(), "requestedPermissions": requested,
        "physicalGrapheneOsTested": False, "instrumentationTestsInThisWorkflow": bool(evidence), "deviceTests": evidence,
        "deviceTestsBuildType": "debug", "optimizedSmokeBuildType": "preview",
        "releaseApkExecutedOnDevice": False}
    (output / "build-info.json").write_text(json.dumps(info, indent=2) + "\n")
    mapping = ROOT / "app" / "build" / "outputs" / "mapping" / args.mode / "mapping.txt"
    if mapping.is_file(): shutil.copy2(mapping, output / "mapping.txt")
    sums = [f"{hashlib.sha256(file.read_bytes()).hexdigest()}  {file.name}" for file in sorted(output.iterdir())]
    (output / "SHA256SUMS").write_text("\n".join(sums) + "\n")
    validate_assets(output, version, revision)
    print(f"Verified and staged {name}; package={expected_id}; signer={fingerprint.group(1).lower()}")
if __name__ == "__main__": main()

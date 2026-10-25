"""Fail-closed checks shared by collection, publication and offline tests."""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import re

REQUIRED_APIS = {31, 35}

def device_evidence(directory: Path, commit: str) -> list[dict]:
    records = []
    for path in sorted(directory.rglob("smoke-result.json")):
        if path.is_symlink():
            raise ValueError("Symlinked test evidence is not accepted.")
        value = json.loads(path.read_text(encoding="utf-8"))
        if value.get("commit") != commit or value.get("status") != "passed":
            raise ValueError("Device evidence must pass at the exact source commit.")
        if value.get("debugTestsPassed", 0) < 1 or value.get("optimizedPreviewStarted") is not True:
            raise ValueError("Missing executed instrumentation or optimized APK smoke test.")
        phases = value.get("debugTestPhases", {})
        if (type(phases) is not dict or set(phases) != {"application", "systemHome", "systemLock"}
                or any(type(v) is not int or v < 1 for v in phases.values())
                or phases["systemHome"] != 2 or phases["systemLock"] != 2
                or sum(phases.values()) != value["debugTestsPassed"]):
            raise ValueError("Complete app, real Home and secure keyguard phases are required.")
        records.append(value)
    if len(records) != len(REQUIRED_APIS) or {v.get("api") for v in records} != REQUIRED_APIS:
        raise ValueError("Passing device evidence is required for API 31 and 35, exactly once each.")
    return sorted(records, key=lambda v: v["api"])

def validate_assets(dist: Path, version: str, commit: str) -> dict:
    if not dist.is_dir() or dist.is_symlink():
        raise ValueError("Invalid release directory.")
    files = list(dist.iterdir())
    if any(p.is_symlink() or not p.is_file() for p in files):
        raise ValueError("Only regular files are allowed as release assets.")
    info = json.loads((dist / "build-info.json").read_text(encoding="utf-8"))
    mode = info.get("buildType")
    if mode not in {"release", "preview"}:
        raise ValueError("Unknown build type.")
    apk = f"vitreo-{version}-{mode}.apk"
    expected = {apk, "build-info.json", "signing-certificate.txt", "SHA256SUMS"}
    names = {p.name for p in files}
    if not expected <= names or names - expected - {"mapping.txt"}:
        raise ValueError("Unexpected or missing release assets.")
    app_id = "dev.vitreo.launcher" + (".preview" if mode == "preview" else "")
    if info.get("apk") != apk or info.get("applicationId") != app_id:
        raise ValueError("Invalid APK filename or application identity.")
    major, minor, patch = map(int, version.split("."))
    if (info.get("versionName"), info.get("versionCode"), info.get("commit")) != (version, major*10000 + minor*100 + patch, commit):
        raise ValueError("Release metadata does not match the checked-out source.")
    for field in ("apkSha256", "signingCertificateSha256"):
        if not re.fullmatch(r"[0-9a-f]{64}", str(info.get(field, ""))):
            raise ValueError(f"Invalid {field}.")
    sums = {}
    for line in (dist / "SHA256SUMS").read_text(encoding="utf-8").splitlines():
        match = re.fullmatch(r"([0-9a-f]{64})  ([A-Za-z0-9_.-]+)", line)
        if not match or match[2] in sums:
            raise ValueError("Malformed, unsafe or duplicate checksum entry.")
        sums[match[2]] = match[1]
    if set(sums) != names - {"SHA256SUMS"}:
        raise ValueError("Checksums do not cover exactly all release payloads.")
    for name, expected_hash in sums.items():
        if hashlib.sha256((dist / name).read_bytes()).hexdigest() != expected_hash:
            raise ValueError(f"Release asset hash mismatch: {name}.")
    if "apkSize" in info and info["apkSize"] != (dist / apk).stat().st_size:
        raise ValueError("APK length differs from build metadata.")
    if info["apkSha256"] != sums[apk]:
        raise ValueError("APK digest differs from build metadata.")
    return info

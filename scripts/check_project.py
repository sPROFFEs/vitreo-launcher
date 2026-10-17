#!/usr/bin/env python3
"""Offline source checks. This is NOT an Android compiler or a security audit."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET
from release_common import ROOT, read_version

def main() -> None:
    version, code = read_version()
    required = ["LICENSE", "THIRD_PARTY_NOTICES.md", "README.md", "BUILDING.md", "gradlew",
        "app/src/main/assets/glass.agsl", "app/src/main/assets/licenses/NOTICE.txt",
        "third_party/folio/LICENSE", ".github/workflows/release.yml", ".github/workflows/ci.yml",
        "scripts/prepare_signing.py", "scripts/github_release.py", "scripts/collect_apk.py"]
    for file in required:
        if not (ROOT / file).is_file(): raise RuntimeError(f"Missing required file: {file}")
    for file in (ROOT / "app/src/main").rglob("*.xml"): ET.parse(file)
    manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
    ns = "{http://schemas.android.com/apk/res/android}"
    allowed_permissions = {"android.permission.BIND_APPWIDGET", "android.permission.SET_WALLPAPER"}
    if any(p.get(ns + "name") not in allowed_permissions for p in manifest.findall("uses-permission")):
        raise RuntimeError("Unexpected permission: only widget binding and system Home wallpaper setting are allowed.")
    activities = manifest.findall("application/activity")
    if not any(i.get(ns + "name") == "android.intent.category.HOME" for a in activities for i in a.findall("intent-filter/category")):
        raise RuntimeError("Missing HOME intent category.")
    if manifest.findall("application/service"): raise RuntimeError("Unexpected Android service.")
    for file in (ROOT / ".github/workflows").glob("*.yml"):
        for action in re.findall(r"\buses:\s+([^\s#]+)", file.read_text()):
            if action.startswith("./.github/workflows/"):
                if not re.fullmatch(r"\./\.github/workflows/[A-Za-z0-9_-]+\.yml", action) or not (ROOT / action).is_file():
                    raise RuntimeError(f"Invalid local reusable workflow: {action}")
                continue
            if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_./-]+@[0-9a-f]{40}", action):
                raise RuntimeError(f"Unpinned third-party action in {file.name}: {action}")
        if "pull_request_target" in file.read_text(): raise RuntimeError("Privileged PR workflow is not permitted.")
    properties = (ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text()
    if "f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6" not in properties:
        raise RuntimeError("Unexpected Gradle distribution checksum; update source checks when upgrading.")
    print(f"SOURCE CHECKS PASSED: Vitreo {version} ({code}); XML, HOME manifest, widget binding/Home wallpaper only, no services, pinned actions, required files.")
if __name__ == "__main__":
    try: main()
    except Exception as exc:
        print(f"Project check failed: {exc}", file=sys.stderr); sys.exit(1)

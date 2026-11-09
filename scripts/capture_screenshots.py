#!/usr/bin/env python3
"""Capture the displayed shipping app; preserve pixels and record provenance.

Requires adb and Pillow. Does not install, reset, configure or post demo content.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import io
import json
from pathlib import Path
import re
import shlex
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="Explicit adb device or emulator serial")
    parser.add_argument("--name", required=True, help="Filename slug, e.g. ios26-home")
    parser.add_argument("--theme", required=True, choices=("ios26", "ios16", "cyberpunk"))
    parser.add_argument("--screen", required=True)
    parser.add_argument("--notes", default="")
    parser.add_argument("--expect-text", help="Require this visible UI text before capture")
    parser.add_argument("--package", default="dev.vitreo.launcher")
    parser.add_argument("--output", type=Path, default=ROOT / "docs/screenshots")
    args = parser.parse_args()
    if not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", args.name):
        parser.error("name must be a lowercase filename slug")
    if not re.fullmatch(r"[a-zA-Z][a-zA-Z0-9_.]+", args.package):
        parser.error("invalid package name")
    try:
        from PIL import Image
    except ImportError:
        parser.error("Pillow is required; install it in your screenshot virtual environment")

    def adb(*arguments: str) -> bytes:
        return subprocess.check_output(["adb", "-s", args.serial, *arguments], timeout=60)

    def shell(*arguments: str) -> str:
        return adb("shell", shlex.join(arguments)).decode().strip()

    focus = shell("dumpsys", "window")
    if not any("mCurrentFocus=" in line and args.package + "/" in line for line in focus.splitlines()):
        parser.error("the requested Vitreo package must own the focused window")
    for option in ("show_touches", "pointer_location"):
        if shell("settings", "get", "system", option) == "1":
            parser.error(f"turn off Android {option} before a documentation capture")
    package = shell("dumpsys", "package", args.package)
    version = re.search(r"\bversionName=([^\s]+)", package)
    code = re.search(r"\bversionCode=(\d+)", package)
    paths = shell("pm", "path", args.package).splitlines()
    if len(paths) != 1 or not paths[0].startswith("package:/data/app/"):
        parser.error("capture requires one installed base APK")
    digest = shell("sha256sum", paths[0].removeprefix("package:")).split()[0]
    if not version or not code or not re.fullmatch(r"[a-f0-9]{64}", digest):
        parser.error("could not verify the installed app identity")
    manifest_file = args.output / "captures.json"
    manifest = json.loads(manifest_file.read_text()) if manifest_file.exists() else {"schema": 1, "captures": []}
    if any(item["apkSha256"] != digest for item in manifest["captures"]):
        parser.error("use a new output directory when changing the screenshot APK")
    if args.expect_text:
        remote = "/sdcard/vitreo-documentation-capture.xml"
        shell("rm", "-f", remote)
        shell("uiautomator", "dump", remote)
        tree = ET.fromstring(shell("cat", remote))
        if not any(node.get("text") == args.expect_text for node in tree.iter("node")):
            parser.error(f"expected screen text is absent: {args.expect_text}")

    raw = adb("exec-out", "screencap", "-p")
    if not raw.startswith(b"\x89PNG\r\n\x1a\n"):
        raise RuntimeError("adb returned an invalid PNG")
    with Image.open(io.BytesIO(raw)) as decoded:
        image = decoded.convert("RGB")
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "thumbs").mkdir(exist_ok=True)
    full = args.output / f"{args.name}.webp"
    thumb = args.output / "thumbs" / full.name
    image.save(full, "WEBP", lossless=True, method=6)
    with Image.open(full) as check:
        if check.convert("RGB").tobytes() != image.tobytes():
            raise RuntimeError("full-size WebP did not preserve screenshot pixels")
    preview = image.resize((360, round(image.height * 360 / image.width)), Image.Resampling.LANCZOS)
    preview.save(thumb, "WEBP", quality=90, method=6)

    record = {
        "file": full.name, "thumbnail": "thumbs/" + thumb.name,
        "theme": args.theme, "screen": args.screen, "notes": args.notes,
        "expectedText": args.expect_text,
        "capturedAtUtc": datetime.now(timezone.utc).isoformat(),
        "package": args.package, "versionName": version.group(1), "versionCode": int(code.group(1)),
        "apkSha256": digest,
        "androidApi": int(shell("getprop", "ro.build.version.sdk")),
        "androidBuild": shell("getprop", "ro.build.fingerprint"),
        "emulator": shell("getprop", "ro.kernel.qemu") == "1",
        "width": image.width, "height": image.height,
        "sourcePngSha256": hashlib.sha256(raw).hexdigest(),
        "sha256": hashlib.sha256(full.read_bytes()).hexdigest(),
        "thumbnailSha256": hashlib.sha256(thumb.read_bytes()).hexdigest(),
        "processing": "Full-size lossless WebP; 360px thumbnail. No crop, retouching or UI compositing.",
    }
    manifest["captures"] = sorted(
        [item for item in manifest["captures"] if item["file"] != record["file"]] + [record],
        key=lambda item: item["file"],
    )
    manifest_file.write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"Captured {record['theme']} / {record['screen']}: {full} ({image.width} × {image.height})")


if __name__ == "__main__":
    main()

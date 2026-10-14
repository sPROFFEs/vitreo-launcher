#!/usr/bin/env python3
"""Run only on a disposable emulator. Records actual results, never assumed success."""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from release_common import ROOT

PACKAGE = "dev.vitreo.launcher.preview"
COMPONENT = PACKAGE + "/dev.vitreo.launcher.MainActivity"

def adb(*args: str) -> str:
    result = subprocess.run(["adb", *args], text=True, capture_output=True, timeout=90)
    if result.returncode:
        raise RuntimeError(f"adb {args}: {result.stderr or result.stdout}")
    return result.stdout

def passed_tests(directory: Path) -> int:
    suites = []
    for path in directory.rglob("TEST-*.xml"):
        root = ET.parse(path).getroot()
        suites.extend([root] if root.tag == "testsuite" else root.findall("testsuite"))
    if not suites:
        raise RuntimeError("No instrumentation JUnit results were produced.")
    total = 0
    for suite in suites:
        if int(suite.get("errors", "0")) or int(suite.get("failures", "0")):
            raise RuntimeError("Instrumentation reported errors or failures.")
        total += int(suite.get("tests", "0")) - int(suite.get("skipped", "0"))
    if total < 1:
        raise RuntimeError("Instrumentation ran no passing tests.")
    return total

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("api", type=int, choices=[31, 35])
    args = parser.parse_args()
    commit = os.environ.get("GITHUB_SHA", "")
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise RuntimeError("Exact GITHUB_SHA is required for CI evidence.")
    # Never clear, install or change the default HOME on an attached personal device.
    if adb("shell", "getprop", "ro.kernel.qemu").strip() != "1":
        raise RuntimeError("Refusing to run this reset/install script on a physical device.")
    if adb("shell", "getprop", "ro.build.version.sdk").strip() != str(args.api):
        raise RuntimeError("Emulator API differs from the required test matrix.")
    output = ROOT / "verification" / "device" / f"api-{args.api}"
    output.mkdir(parents=True, exist_ok=True)
    result_path = output / "smoke-result.json"
    result_path.unlink(missing_ok=True)
    try:
        subprocess.run(["bash", "gradlew", "--no-daemon", "--console=plain", ":app:connectedDebugAndroidTest"], cwd=ROOT, check=True)
        count = passed_tests(ROOT / "app/build/outputs/androidTest-results/connected")
        apks = list((ROOT / "app/build/outputs/apk/preview").glob("*.apk"))
        if len(apks) != 1:
            raise RuntimeError("Expected exactly one optimized Preview APK.")
        adb("install", "-r", str(apks[0]))
        adb("shell", "pm", "clear", PACKAGE)
        adb("shell", "cmd", "package", "set-home-activity", COMPONENT)
        adb("logcat", "-c")
        start = adb("shell", "am", "start", "-W", "-n", COMPONENT)
        (output / "optimized-start.txt").write_text(start)
        if "Status: ok" not in start or "Error:" in start:
            raise RuntimeError("Optimized Preview failed to start.")
        time.sleep(2)
        original_pid = adb("shell", "pidof", PACKAGE).strip()
        if not original_pid:
            raise RuntimeError("Optimized Preview exited after launch.")
        adb("shell", "input", "keyevent", "KEYCODE_HOME")
        time.sleep(2)
        adb("shell", "uiautomator", "dump", "/sdcard/vitreo-smoke.xml")
        adb("pull", "/sdcard/vitreo-smoke.xml", str(output / "window.xml"))
        nodes = ET.parse(output / "window.xml").getroot().iter("node")
        if not any(n.get("package") == PACKAGE and n.get("text") == "Buscar" for n in nodes):
            raise RuntimeError("The optimized launcher UI is not visible after the HOME key.")
        screenshot = subprocess.check_output(["adb", "exec-out", "screencap", "-p"], timeout=60)
        if not screenshot.startswith(bytes([137, 80, 78, 71, 13, 10, 26, 10])):
            raise RuntimeError("Invalid emulator screenshot.")
        (output / "optimized-home.png").write_bytes(screenshot)
        if adb("shell", "pidof", PACKAGE).strip() != original_pid:
            raise RuntimeError("The optimized launcher process restarted unexpectedly.")
        crash_log = adb("logcat", "-b", "crash", "-d")
        (output / "crash-log.txt").write_text(crash_log)
        if "FATAL EXCEPTION" in crash_log or "Fatal signal" in crash_log:
            raise RuntimeError("A fatal crash occurred during optimized APK smoke testing.")
        result_path.write_text(json.dumps({"schema": 1, "commit": commit, "api": args.api,
            "status": "passed", "debugTestsPassed": count, "optimizedPreviewStarted": True,
            "physicalGrapheneOsTested": False}, indent=2) + "\n")
    finally:
        try:
            (output / "logcat.txt").write_text(adb("logcat", "-d", "-t", "3000"))
        except Exception as exc:
            (output / "logcat-unavailable.txt").write_text(type(exc).__name__)

if __name__ == "__main__":
    main()

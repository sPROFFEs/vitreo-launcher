#!/usr/bin/env python3
"""Explicit destructive fixture for a disposable emulator, never a physical device.

ADB installs the baseline before this script; only Vitreo commits the candidate.
The package replacement intentionally kills the instrumentation sender.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
from release_common import ROOT, read_version

PACKAGE = "dev.vitreo.launcher.debug"

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default="emulator-5554")
    parser.add_argument("--output", type=Path, default=ROOT / "out/verification/080-local/self-update")
    args = parser.parse_args()
    adb_executable = str(Path(os.environ["ANDROID_HOME"]) / "platform-tools/adb") if os.environ.get("ANDROID_HOME") else "adb"
    prefix = [adb_executable, "-s", args.serial]
    def adb(*commands):
        return subprocess.check_output([*prefix, *commands], text=True, stderr=subprocess.STDOUT, timeout=90)
    assert args.serial.startswith("emulator-") and adb("shell", "getprop", "ro.kernel.qemu").strip() == "1", "Refusing a physical device."
    def installed():
        dump = adb("shell", "dumpsys", "package", PACKAGE)
        match = re.search(r"versionCode=(\d+)\b", dump)
        return int(match[1]) if match else None
    version, code = read_version()
    candidate_version = version.rsplit(".", 1)[0] + "." + str(int(version.rsplit(".", 1)[1]) + 1)
    assert installed() == code, "Install the current disposable Debug baseline first."
    candidate = ROOT / "app/build/outputs/apk/updateFixture/app-updateFixture.apk"
    assert candidate.is_file(), "Build :app:assembleUpdateFixture first."
    home = adb("shell", "cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME").strip().splitlines()[-1]
    assert re.fullmatch(r"[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+", home), "Unexpected Home component."
    appop = adb("shell", "appops", "get", PACKAGE, "REQUEST_INSTALL_PACKAGES")
    mode = re.search(r"REQUEST_INSTALL_PACKAGES:\s*(allow|deny|ignore|default)", appop)
    mode = mode[1] if mode else "default"
    output = args.output.resolve(); output.mkdir(parents=True, exist_ok=True)
    try:
        adb("shell", "cmd", "package", "set-home-activity", "com.android.launcher3/.uioverrides.QuickstepLauncher")
        adb("shell", "appops", "set", PACKAGE, "REQUEST_INSTALL_PACKAGES", "allow")
        adb("shell", "mkdir", "-p", f"/sdcard/Android/data/{PACKAGE}/files")
        adb("push", str(candidate), f"/sdcard/Android/data/{PACKAGE}/files/update-probe.apk")
        adb("logcat", "-c")
        result = subprocess.run([*prefix, "shell", "am", "instrument", "-w", "-e", "version", candidate_version, "-e", "code", str(code+1),
            PACKAGE + ".test/dev.vitreo.launcher.SelfUpdateProbe"], text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=60)
        (output / "stage.txt").write_text(result.stdout)
        assert "stage=passed" in result.stdout, result.stdout
        deadline = time.monotonic() + 30
        while installed() != code+1 and time.monotonic() < deadline:
            time.sleep(1)
        assert installed() == code+1, "Native package replacement did not complete; see device logcat."
        report = {"beforeVersionCode":code,"afterVersionCode":code+1,"candidateVersion":candidate_version,
                  "candidateSha256":hashlib.sha256(candidate.read_bytes()).hexdigest(),"adbInstalledTheCandidate":False,
                  "installer":"SelfUpdateInstaller.stage/commit → Android PackageInstaller", "instrumentationExitCode":result.returncode}
        (output / "result.json").write_text(json.dumps(report, indent=2) + "\n")
        print(json.dumps(report, indent=2))
    finally:
        (output / "logcat.txt").write_text(adb("logcat", "-d", "-v", "threadtime"))
        adb("shell", "appops", "set", PACKAGE, "REQUEST_INSTALL_PACKAGES", mode)
        adb("shell", "cmd", "package", "set-home-activity", home)
        adb("shell", "rm", "-f", f"/sdcard/Android/data/{PACKAGE}/files/update-probe.apk")

if __name__ == "__main__":
    main()

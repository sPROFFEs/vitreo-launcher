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
from instrumentation_results import write_junit
from release_validation import REQUIRED_HOME_TESTS, REQUIRED_LOCK_TESTS

PACKAGE = "dev.vitreo.launcher.preview"
COMPONENT = PACKAGE + "/dev.vitreo.launcher.MainActivity"
SYSTEM_HOME_TEST = "dev.vitreo.launcher.HomeIntegrationTest"
SYSTEM_LOCK_TEST = "dev.vitreo.launcher.LockScreenDeviceTest"

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

def read_ui(output: Path, name: str) -> ET.Element:
    adb("shell", "uiautomator", "dump", "/sdcard/vitreo-smoke.xml")
    path = output / f"{name}.xml"
    adb("pull", "/sdcard/vitreo-smoke.xml", str(path))
    return ET.parse(path).getroot()

def has_launcher_text(root: ET.Element, expected: str) -> bool:
    return any(node.get("package") == PACKAGE and node.get("text") == expected for node in root.iter("node"))

def capture_screen(output: Path, name: str, expected: str) -> ET.Element:
    deadline = time.monotonic() + 15
    while True:
        root = read_ui(output, name)
        if has_launcher_text(root, expected):
            break
        if time.monotonic() >= deadline:
            raise RuntimeError(f"Expected {expected!r} on optimized {name} screen.")
        time.sleep(.3)
    image = subprocess.check_output(["adb", "exec-out", "screencap", "-p"], timeout=60)
    if not image.startswith(bytes([137, 80, 78, 71, 13, 10, 26, 10])):
        raise RuntimeError("Invalid emulator screenshot.")
    (output / f"optimized-{name}.png").write_bytes(image)
    return root

def navigate_page(output: Path, name: str, expected: str, swipe, max_pages: int = 10) -> ET.Element:
    # Navigate from observed state: a delayed HOME/recreated window can swallow
    # an injected gesture. The target is still mandatory; no failed test is retried.
    for _ in range(max_pages):
        if has_launcher_text(read_ui(output, name), expected):
            return capture_screen(output, name, expected)
        swipe()
    return capture_screen(output, name, expected)

def app_node(root: ET.Element, label: str) -> ET.Element:
    # Editable fields can be long-clickable and contain exactly the app's name.
    # App labels may instead be children of their clickable tile in UIAutomator.
    node = next((node for node in root.iter("node") if node.get("package") == PACKAGE and
        node.get("class") != "android.widget.EditText" and node.get("clickable") == "true" and
        node.get("long-clickable") == "true" and
        any(child.get("text") == label or child.get("content-desc") == label for child in node.iter("node"))), None)
    if node is None:
        raise RuntimeError(f"App {label!r} missing from optimized results.")
    return node

def wait_for_app(package: str) -> None:
    deadline = time.monotonic() + 20
    while True:
        activities = adb("shell", "dumpsys", "activity", "activities")
        if any(("mResumedActivity" in line or "topResumedActivity" in line) and f"{package}/" in line
               for line in activities.splitlines()):
            return
        if time.monotonic() >= deadline:
            raise RuntimeError(f"The app {package} did not become resumed.")
        time.sleep(.2)

def tap_node(root: ET.Element, attribute: str, value: str) -> None:
    node = next((node for node in root.iter("node") if node.get(attribute) == value), None)
    if node is None:
        raise RuntimeError(f"Missing UI control: {value}.")
    bounds = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not bounds:
        raise RuntimeError("Invalid UI control bounds.")
    left, top, right, bottom = map(int, bounds.groups())
    adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))

def tap_settings_row(root: ET.Element, title: str) -> None:
    # Match the row title, not a detail value such as Today & News → Widgets.
    rows = [node for node in root.iter("node") if node.get("package") == PACKAGE
            and node.get("clickable") == "true"
            and next((child.get("text") for child in node.iter("node") if child.get("text")), None) == title]
    if len(rows) != 1:
        raise RuntimeError(f"Expected exactly one settings row titled {title!r}.")
    tap_node(rows[0], "clickable", "true")

def collect_native_screens(output: Path, names: list[str]) -> None:
    for name in names:
        image = output / f"native-{name}.png"
        adb("pull", f"/sdcard/Android/data/dev.vitreo.launcher.debug/files/review/{name}.png", str(image))
        data = image.read_bytes()
        if not data.startswith(bytes([137, 80, 78, 71, 13, 10, 26, 10])):
            raise RuntimeError(f"Invalid native screenshot {name}: {len(data)} bytes; header {data[:16].hex()}.")

def prepare_emulator_home() -> None:
    """One clean stock Home start after APK-install broadcasts, before any test runs.

    A first-boot Pixel Launcher ANR can otherwise cover every ActivityScenario.
    Only known image launchers are restarted; any ANR after this reset fails fast.
    Vitreo crashes, ANRs and failed tests are never dismissed or retried.
    """
    if adb("shell", "getprop", "ro.kernel.qemu").strip() != "1":
        raise RuntimeError("Stock Home preparation requires a disposable emulator.")
    component = None
    for package in ("com.google.android.apps.nexuslauncher", "com.android.launcher3"):
        resolved = adb("shell", "cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN",
            "-c", "android.intent.category.HOME", "-p", package)
        component = next((line.strip() for line in resolved.splitlines()
            if re.fullmatch(re.escape(package) + r"/[A-Za-z0-9_.$]+", line.strip())), None)
        if component: break
    if component is None:
        raise RuntimeError("No known stock emulator Home activity was found.")
    adb("shell", "am", "force-stop", component.split("/")[0])
    start = adb("shell", "am", "start", "-W", "-n", component, "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME")
    if "Status: ok" not in start or "Error:" in start:
        raise RuntimeError("Stock emulator Home failed its initial clean start.")
    deadline = time.monotonic() + 20
    focused_since = None
    while True:
        # The "windows" subcommand omits mCurrentFocus on API 31/35.
        # The complete dump includes the display focus section used by the UI suite.
        window = adb("shell", "dumpsys", "window")
        focus = "\n".join(line for line in window.splitlines() if "mCurrentFocus" in line)
        if "Application Not Responding" in focus:
            raise RuntimeError("ANR blocks emulator UI after initial preparation: " + focus.strip())
        now = time.monotonic()
        if component.split("/")[0] + "/" in focus:
            if focused_since is None: focused_since = now
            if now - focused_since >= 1:
                print("Stock emulator Home has stable focus; starting native tests.", flush=True)
                return
        else: focused_since = None
        if now >= deadline:
            raise RuntimeError("Stock emulator Home never gained stable focus: " + focus.strip())
        time.sleep(.2)

def run_instrumentation(system_home: bool = False, system_lock: bool = False) -> int:
    if system_home and system_lock: raise ValueError("Select only one isolated device phase.")
    # APKs are built once, then downloaded by both disposable emulator jobs.
    if not system_home and not system_lock:
        for directory in ("app/build/outputs/apk/debug", "app/build/outputs/apk/androidTest/debug"):
            apks = list((ROOT / directory).glob("*.apk"))
            if len(apks) != 1: raise RuntimeError(f"Expected one prebuilt APK in {directory}.")
            adb("install", "-r", "-t", str(apks[0]))
        prepare_emulator_home()
    reports = ROOT / "app/build/outputs/androidTest-results/connected"
    reports.mkdir(parents=True, exist_ok=True)
    name = "system-lock" if system_lock else "system-home" if system_home else "instrumentation"
    selected = SYSTEM_LOCK_TEST if system_lock else SYSTEM_HOME_TEST if system_home else f"{SYSTEM_HOME_TEST},{SYSTEM_LOCK_TEST}"
    adb("logcat", "-c")
    with (reports / f"{name}-logcat.txt").open("w") as log:
        logging = subprocess.Popen(["adb", "logcat", "-v", "threadtime"], stdout=log, stderr=subprocess.STDOUT)
        try:
            result = subprocess.run(["adb", "shell", "am", "instrument", "-w", "-r",
                "-e", "class" if system_home or system_lock else "notClass", selected,
                "dev.vitreo.launcher.debug.test/androidx.test.runner.AndroidJUnitRunner"],
                cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=1200)
            (reports / f"{name}-output.txt").write_text(result.stdout)
            print(result.stdout, flush=True)
            report = reports / f"TEST-{name}.xml"
            count = write_junit(result.stdout, report)
            if result.returncode: raise RuntimeError(f"Instrumentation command failed: {result.returncode}")
            classes = {case.get("classname") for case in ET.parse(report).getroot().findall("testcase")}
            if system_home and (classes != {SYSTEM_HOME_TEST} or count != REQUIRED_HOME_TESTS):
                raise RuntimeError("All three real Home integration tests must execute in their isolated phase.")
            if system_lock and (classes != {SYSTEM_LOCK_TEST} or count != REQUIRED_LOCK_TESTS):
                raise RuntimeError("Both real keyguard tests must execute in their isolated phase.")
            if not system_lock and SYSTEM_LOCK_TEST in classes:
                raise RuntimeError("Secure keyguard tests must execute only in the final isolated phase.")
            if not system_home and SYSTEM_HOME_TEST in classes:
                raise RuntimeError("System Home tests must not run before the app UI/optimized checks.")
            return count
        finally:
            logging.terminate()
            try: logging.wait(timeout=10)
            except subprocess.TimeoutExpired: logging.kill(); logging.wait()


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
    immersive = adb("shell", "settings", "get", "secure", "immersive_mode_confirmations").strip()
    try:
        # The system's one-time tutorial is tested by Android, not by this UI suite.
        # Keep it from covering a full-screen Dialog or swallowing injected gestures.
        adb("shell", "settings", "put", "secure", "immersive_mode_confirmations", "confirmed")
        fixtures = list((ROOT / "test-fixtures/uninstall-app/build/outputs/apk/debug").glob("*.apk"))
        if len(fixtures) != 1:
            raise RuntimeError("Build :uninstallFixture:assembleDebug before device smoke tests.")
        adb("push", str(fixtures[0]), "/data/local/tmp/vitreo-uninstall-fixture.apk")
        application_count = run_instrumentation()
        native_screens = ["category-light", "category-dark", "settings-dark", "wallpaper-fit", "app-menu", "app-menu-more", "alphabetical-library", "installed-icon", "large-icons", "home-widget-small", "home-widget-medium",
            "cyber-settings", "cyber-home", "cyber-menu", "cyber-library", "cyber-category", "recents-return", "animated-dock", "today-customized", "control-center", "control-center-edit", "control-center-media", "control-center-connectivity", "cyber-immersive", "control-picker", "theme-icon-picker", "theme-icon-applied", "theme-icon-cyber", "home-status-bar", "control-center-session", "notification-center", "lock-ios", "lock-cyber", "lock-large-clock", "lock-editor", "cyber-notification-center", "cyber-status-bar", "cyber-control-center"]
        native_screens += ["island-expanded-position", "island-collapsed-position"]
        if args.api >= 33:
            native_screens += [f"glass-{material}-{effect}" for material in ("clear", "regular") for effect in ("flat", "refracted")]
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
        if not any(n.get("package") == PACKAGE and n.get("text") == "Search" for n in nodes):
            raise RuntimeError("The optimized launcher UI is not visible after the HOME key.")
        screenshot = subprocess.check_output(["adb", "exec-out", "screencap", "-p"], timeout=60)
        if not screenshot.startswith(bytes([137, 80, 78, 71, 13, 10, 26, 10])):
            raise RuntimeError("Invalid emulator screenshot.")
        (output / "optimized-home.png").write_bytes(screenshot)
        size = re.search(r"(\d+)x(\d+)", adb("shell", "wm", "size"))
        if not size:
            raise RuntimeError("Cannot determine emulator screen size.")
        width, height = map(int, size.groups())
        def swipe(start: float, end: float) -> None:
            adb("shell", "input", "swipe", str(int(width * start)), str(int(height * .48)),
                str(int(width * end)), str(int(height * .48)), "350")
            time.sleep(.7)
        today = navigate_page(output, "today", "Today", lambda: swipe(.2, .8), max_pages=3)
        tap_node(today, "content-desc", "Settings")
        time.sleep(.7)
        settings = capture_screen(output, "settings", "Settings")
        tap_settings_row(settings, "Motion & Glass")
        time.sleep(.7)
        capture_screen(output, "glass-settings", "Motion & Glass")
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(.7)
        settings = capture_screen(output, "settings", "Settings")
        tap_settings_row(settings, "Wallpaper")
        time.sleep(.7)
        capture_screen(output, "wallpaper-settings", "Wallpaper")
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(.7)
        settings = capture_screen(output, "settings", "Settings")
        tap_settings_row(settings, "Widgets")
        time.sleep(.7)
        capture_screen(output, "widgets", "Add what matters")
        adb("shell", "input", "swipe", str(width // 2), str(int(height * .75)), str(width // 2), str(int(height * .3)), "350")
        time.sleep(.7)
        capture_screen(output, "widget-previews", "Widgets")
        adb("shell", "input", "keyevent", "KEYCODE_HOME")
        time.sleep(.7)
        home = capture_screen(output, "home", "Search")
        tap_node(home, "text", "Search")
        time.sleep(.7)
        capture_screen(output, "spotlight", "Cancel")
        adb("shell", "input", "keyevent", "KEYCODE_HOME")
        time.sleep(.7)
        library = navigate_page(output, "library", "App Library", lambda: swipe(.8, .2))
        # Tap the gap in the first category card, avoiding its individual app buttons.
        label = next(node for node in library.iter("node") if node.get("text") == "Suggestions")
        bounds = list(map(int, re.findall(r"\d+", label.get("bounds", ""))))
        density_values = re.findall(r"density: (\d+)", adb("shell", "wm", "density"))
        density = int(density_values[-1]) / 160
        category_x, category_y = (bounds[0] + bounds[2]) // 2, int(bounds[1] - 85 * density)
        adb("shell", "input", "tap", str(category_x), str(category_y))
        time.sleep(.7)
        category = capture_screen(output, "category", "Suggestions")
        # Open the actual first category icon; preserve the category under its menu.
        app_nodes = [node for node in category.iter("node") if node.get("clickable") == "true" and
            node.get("long-clickable") == "true" and node.get("bounds")]
        if not app_nodes:
            raise RuntimeError("No app in the expanded library category.")
        bounds = list(map(int, re.findall(r"\d+", app_nodes[0].get("bounds", ""))))
        icon_x, icon_y = (bounds[0] + bounds[2]) // 2, (bounds[1] + bounds[3]) // 2
        adb("shell", "input", "swipe", str(icon_x), str(icon_y), str(icon_x), str(icon_y), "700")
        time.sleep(.7)
        menu = capture_screen(output, "app-menu", "Edit Home Screen")
        tap_node(menu, "text", "More")
        time.sleep(.7)
        capture_screen(output, "app-menu-more", "App Info")
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(.7)
        capture_screen(output, "category-return", "Suggestions")
        adb("shell", "input", "keyevent", "KEYCODE_HOME")
        time.sleep(.7)
        adb("shell", "input", "swipe", str(int(width * .85)), str(int(height * .09)),
            str(int(width * .85)), str(int(height * .45)), "350")
        time.sleep(.7)
        controls = capture_screen(output, "controls", "Focus")
        if not any(node.get("package") == PACKAGE and node.get("content-desc") == "Internet settings"
                   and node.get("clickable") == "true" for node in controls.iter("node")):
            raise RuntimeError("Optimized Control Center is missing its accessible Internet control.")
        adb("shell", "input", "keyevent", "KEYCODE_HOME")
        # Native tests use disabled system animations; record the optimized app with real motion enabled as well.
        for setting in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
            adb("shell", "settings", "put", "global", setting, "1")
        adb("shell", "dumpsys", "gfxinfo", PACKAGE, "reset")
        recording = subprocess.Popen(["adb", "shell", "screenrecord", "--time-limit", "40", "--size", "540x1200", "--bit-rate", "2000000", "/sdcard/vitreo-motion.mp4"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            time.sleep(1)
            # Spotlight open/close, actual app switch/return, then Library's anchored menu.
            adb("shell", "input", "swipe", str(width // 2), str(int(height * .35)), str(width // 2), str(int(height * .65)), "450")
            time.sleep(1)
            adb("shell", "input", "text", "Settings")
            time.sleep(1)
            search = capture_screen(output, "motion-app-search", "Apps")
            app = app_node(search, "Settings")
            app_bounds = list(map(int, re.findall(r"\d+", app.get("bounds", ""))))
            adb("shell", "input", "tap", str((app_bounds[0] + app_bounds[2]) // 2), str((app_bounds[1] + app_bounds[3]) // 2))
            wait_for_app("com.android.settings")
            time.sleep(.5)
            adb("shell", "input", "keyevent", "KEYCODE_HOME")
            time.sleep(1)
            capture_screen(output, "app-return", "Apps")
            adb("shell", "input", "keyevent", "KEYCODE_HOME")
            time.sleep(1)
            adb("shell", "input", "keyevent", "KEYCODE_APP_SWITCH")
            time.sleep(1)
            adb("shell", "input", "keyevent", "KEYCODE_HOME")
            time.sleep(1)
            capture_screen(output, "recents-return", "Search")
            navigate_page(output, "motion-library", "App Library", lambda: swipe(.8, .2))
            # Reuse the same first category's centre, independently of icon bounds.
            adb("shell", "input", "tap", str(category_x), str(category_y))
            time.sleep(1)
            adb("shell", "input", "swipe", str(icon_x), str(icon_y), str(icon_x), str(icon_y), "700")
            time.sleep(1)
            adb("shell", "input", "keyevent", "KEYCODE_BACK")
            time.sleep(1)
            adb("shell", "input", "keyevent", "KEYCODE_BACK")
            recording.wait(timeout=50)
            adb("pull", "/sdcard/vitreo-motion.mp4", str(output / "optimized-motion.mp4"))
        finally:
            if recording.poll() is None:
                recording.terminate()
        (output / "optimized-frame-stats.txt").write_text(adb("shell", "dumpsys", "gfxinfo", PACKAGE, "framestats"))
        if adb("shell", "pidof", PACKAGE).strip() != original_pid:
            raise RuntimeError("The optimized launcher process restarted unexpectedly.")
        crash_log = adb("logcat", "-b", "crash", "-d")
        (output / "crash-log.txt").write_text(crash_log)
        if "FATAL EXCEPTION" in crash_log or "Fatal signal" in crash_log:
            raise RuntimeError("A fatal crash occurred during optimized APK smoke testing.")
        # HOME role/Quickstep changes affect the whole emulator. Run these last,
        # in a fresh instrumentation process, so their teardown cannot steal
        # input focus from later ActivityScenario windows or optimized Preview.
        system_home_count = run_instrumentation(system_home=True)
        system_lock_count = run_instrumentation(system_lock=True)
        count = application_count + system_home_count + system_lock_count
        if passed_tests(ROOT / "app/build/outputs/androidTest-results/connected") != count:
            raise RuntimeError("The three native phases must account for exactly all passing test reports.")
        native_screens.extend(["theme-ios16-settings", "theme-ios16-home", "theme-ios16-controls", "theme-ios26-controls"])
        collect_native_screens(output, native_screens)
        result_path.write_text(json.dumps({"schema": 1, "commit": commit, "api": args.api,
            "status": "passed", "debugTestsPassed": count, "optimizedPreviewStarted": True,
            "debugTestPhases": {"application": application_count, "systemHome": system_home_count, "systemLock": system_lock_count},
            "uiScreens": ["home", "today", "settings", "glass-settings", "wallpaper-settings", "widgets", "widget-previews", "spotlight", "library", "category", "app-menu", "app-menu-more", "category-return", "controls", "motion-app-search", "app-return", "recents-return"],
            "motionRecordedWithAnimationsEnabled": True,
            "nativeScreens": native_screens,
            "physicalGrapheneOsTested": False}, indent=2) + "\n")
    finally:
        try:
            if immersive in ("", "null"):
                adb("shell", "settings", "delete", "secure", "immersive_mode_confirmations")
            else:
                adb("shell", "settings", "put", "secure", "immersive_mode_confirmations", immersive)
        except Exception as exc:
            (output / "immersive-restore-failed.txt").write_text(type(exc).__name__)
        if not result_path.exists():
            try:
                # Failed instrumentation must retain the screens captured before
                # its assertion, even though optimized smoke never started.
                adb("pull", "/sdcard/Android/data/dev.vitreo.launcher.debug/files/review", str(output / "native-failure"))
            except Exception as exc:
                (output / "native-screens-unavailable.txt").write_text(type(exc).__name__)
        try:
            (output / "logcat.txt").write_text(adb("logcat", "-d", "-t", "3000"))
        except Exception as exc:
            (output / "logcat-unavailable.txt").write_text(type(exc).__name__)
        for name, command in (("window.txt", ("dumpsys", "window")),
                              ("last-anr.txt", ("dumpsys", "activity", "lastanr"))):
            try: (output / name).write_text(adb("shell", *command))
            except Exception as exc: (output / (name + ".unavailable")).write_text(type(exc).__name__)

if __name__ == "__main__":
    main()

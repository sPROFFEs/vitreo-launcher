"""Narrow manifest policy shared by source checks and compiled-APK verification."""
import re
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
PERMISSIONS = {"android.permission.BIND_APPWIDGET", "android.permission.SET_WALLPAPER", "android.permission.ACCESS_NETWORK_STATE", "android.permission.INTERNET", "android.permission.REQUEST_INSTALL_PACKAGES", "android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION", "android.permission.POST_NOTIFICATIONS"}
LISTENER = "dev.vitreo.launcher.data.VitreoNotificationListener"
BIND = "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
ACTION = "android.service.notification.NotificationListenerService"

def validate_source(manifest):
    requested = {p.get(ANDROID + "name") for p in manifest.findall("uses-permission")}
    if requested != PERMISSIONS:
        raise RuntimeError(f"Unexpected permission set: {requested}")
    locks = [a for a in manifest.findall("application/activity")
             if a.get(ANDROID + "name") in {".lock.LockScreenActivity", "dev.vitreo.launcher.lock.LockScreenActivity"}]
    if len(locks) != 1:
        raise RuntimeError("Exactly one private lock-screen activity is required.")
    lock = locks[0]
    if (lock.get(ANDROID + "exported") != "false" or lock.findall("intent-filter")
            or lock.get(ANDROID + "excludeFromRecents") != "true"
            or lock.get(ANDROID + "taskAffinity") != "${applicationId}.lock"
            or lock.get(ANDROID + "turnScreenOn") == "true"
            or lock.get(ANDROID + "directBootAware") == "true"):
        raise RuntimeError("Lock surface must be private, separate from HOME, excluded from Recents and never wake the screen.")
    services = manifest.findall("application/service")
    if len(services) != 2:
        raise RuntimeError("Only the notification listener and protected update job are allowed.")
    job = services[1]
    if (job.get(ANDROID + "name") not in {".update.UpdateJobService", "dev.vitreo.launcher.update.UpdateJobService"}
            or job.get(ANDROID + "permission") != "android.permission.BIND_JOB_SERVICE"
            or job.get(ANDROID + "exported") != "true" or job.findall("intent-filter") or job.get(ANDROID + "foregroundServiceType")):
        raise RuntimeError("Update jobs must be bound only by the system scheduler.")
    for kind, name in (("receiver", "UpdateInstallReceiver"), ("activity", "UpdatesActivity")):
        components = [c for c in manifest.findall("application/" + kind) if c.get(ANDROID + "name") in {".update." + name, "dev.vitreo.launcher.update." + name}]
        if len(components) != 1 or components[0].get(ANDROID + "exported") != "false" or components[0].findall("intent-filter"):
            raise RuntimeError("Update callbacks and screens must be private.")
    service = services[0]
    if (service.get(ANDROID + "name") not in {LISTENER, ".data.VitreoNotificationListener"}
            or service.get(ANDROID + "permission") != BIND or service.get(ANDROID + "exported") != "false"):
        raise RuntimeError("Notification listener must be non-exported and protected by Android's binding permission.")
    actions = [a.get(ANDROID + "name") for a in service.findall("intent-filter/action")]
    if actions != [ACTION] or service.get(ANDROID + "foregroundServiceType"):
        raise RuntimeError("Unexpected service contract.")

def validate_apk_services(tree):
    # aapt dump xmltree indentation identifies components and their entire subtrees.
    services = [m.group(0) for m in re.finditer(r"(?ms)^(?P<indent> +)E: service\b.*?(?=^(?P=indent)E:|\Z)", tree)]
    if len(services) != 2:
        raise RuntimeError("APK must contain exactly the listener and system-bound update job.")
    job = next((s for s in services if '="dev.vitreo.launcher.update.UpdateJobService"' in s), "")
    if ('="android.permission.BIND_JOB_SERVICE"' not in job or "intent-filter" in job or "foregroundServiceType" in job
            or not re.search(r"android:exported\([^\n]*\)=\(type 0x12\)0xffffffff\b", job)):
        raise RuntimeError("APK update job must be protected by the system binding permission.")
    services = [s for s in services if s != job]
    service = services[0]
    for value in (LISTENER, BIND, ACTION):
        if f'="{value}"' not in service:
            raise RuntimeError(f"APK listener contract missing {value}")
    if not re.search(r"android:exported\([^\n]*\)=\(type 0x12\)0x0\b", service):
        raise RuntimeError("APK notification listener must not be exported.")
    if "foregroundServiceType" in service:
        raise RuntimeError("No foreground service is authorized.")


def validate_apk_lock_activity(tree, application_id="dev.vitreo.launcher"):
    activities = [m.group(0) for m in re.finditer(r"(?ms)^(?P<indent> +)E: activity\b.*?(?=^(?P=indent)E:|\Z)", tree)]
    locks = [a for a in activities if '="dev.vitreo.launcher.lock.LockScreenActivity"' in a]
    if len(locks) != 1:
        raise RuntimeError("APK must contain exactly one private lock-screen activity.")
    lock = locks[0]
    for attribute, value in (("exported", "0x0"), ("excludeFromRecents", "0xffffffff")):
        if not re.search(rf"android:{attribute}\([^\n]*\)=\(type 0x12\){value}\b", lock):
            raise RuntimeError(f"APK lock-screen contract failed: {attribute}")
    if 'intent-filter' in lock or f'="{application_id}.lock"' not in lock:
        raise RuntimeError("APK lock surface must use its own task and expose no intent filter.")
    for attribute in ("turnScreenOn", "directBootAware"):
        if re.search(rf"android:{attribute}\([^\n]*\)=\(type 0x12\)0xffffffff\b", lock):
            raise RuntimeError("APK lock surface must not wake or run before first unlock.")


def validate_apk_updates(tree):
    for kind, name in (("receiver", "UpdateInstallReceiver"), ("activity", "UpdatesActivity")):
        components = [m.group(0) for m in re.finditer(r"(?ms)^(?P<indent> +)E: " + kind + r"\b.*?(?=^(?P=indent)E:|\Z)", tree)]
        matching = [c for c in components if '="dev.vitreo.launcher.update.' + name + '"' in c]
        if (len(matching) != 1 or "intent-filter" in matching[0]
                or not re.search(r"android:exported\([^\n]*\)=\(type 0x12\)0x0\b", matching[0])):
            raise RuntimeError("APK update callbacks and screens must remain private.")

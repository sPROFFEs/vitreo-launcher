"""Narrow manifest policy shared by source checks and compiled-APK verification."""
import re
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
PERMISSIONS = {"android.permission.BIND_APPWIDGET", "android.permission.SET_WALLPAPER", "android.permission.ACCESS_NETWORK_STATE"}
LISTENER = "dev.vitreo.launcher.data.VitreoNotificationListener"
BIND = "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
ACTION = "android.service.notification.NotificationListenerService"

def validate_source(manifest):
    requested = {p.get(ANDROID + "name") for p in manifest.findall("uses-permission")}
    if requested != PERMISSIONS:
        raise RuntimeError(f"Unexpected permission set: {requested}")
    services = manifest.findall("application/service")
    if len(services) != 1:
        raise RuntimeError("Only the optional system-bound notification listener is allowed.")
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
    if len(services) != 1:
        raise RuntimeError("APK must contain exactly the authorized notification listener service.")
    service = services[0]
    for value in (LISTENER, BIND, ACTION):
        if f'="{value}"' not in service:
            raise RuntimeError(f"APK listener contract missing {value}")
    if not re.search(r"android:exported\([^\n]*\)=\(type 0x12\)0x0\b", service):
        raise RuntimeError("APK notification listener must not be exported.")
    if "foregroundServiceType" in service:
        raise RuntimeError("No foreground service is authorized.")

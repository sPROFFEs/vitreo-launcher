import copy
from pathlib import Path
import sys
import unittest
import xml.etree.ElementTree as ET
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from manifest_policy import validate_source, validate_apk_services, ANDROID, LISTENER, BIND, ACTION, validate_apk_lock_activity

class ManifestPolicyTests(unittest.TestCase):
    def setUp(self):
        self.manifest = ET.parse(Path(__file__).resolve().parents[1] / "app/src/main/AndroidManifest.xml").getroot()
    def test_current_manifest_is_accepted(self):
        validate_source(self.manifest)
    def test_exported_listener_is_rejected(self):
        self.manifest.find("application/service").set(ANDROID + "exported", "true")
        with self.assertRaises(RuntimeError): validate_source(self.manifest)
    def test_unprotected_listener_is_rejected(self):
        self.manifest.find("application/service").attrib.pop(ANDROID + "permission")
        with self.assertRaises(RuntimeError): validate_source(self.manifest)
    def test_extra_service_is_rejected(self):
        self.manifest.find("application").append(copy.deepcopy(self.manifest.find("application/service")))
        with self.assertRaises(RuntimeError): validate_source(self.manifest)
    def test_extra_permission_is_rejected(self):
        ET.SubElement(self.manifest, "uses-permission", {ANDROID + "name": "android.permission.INTERNET"})
        with self.assertRaises(RuntimeError): validate_source(self.manifest)
    def test_binary_manifest_requires_binding_export_and_action(self):
        tree = f'''    E: service (line=12)
      A: android:name(0x01010003)="{LISTENER}" (Raw: "{LISTENER}")
      A: android:permission(0x01010006)="{BIND}"
      A: android:exported(0x01010010)=(type 0x12)0x0
      E: intent-filter (line=13)
        E: action (line=14)
          A: android:name(0x01010003)="{ACTION}"
    E: activity (line=15)
'''
        validate_apk_services(tree)
        validate_apk_services("\n".join("  " + line for line in tree.splitlines()))
        for bad in (tree.replace('0x12)0x0', '0x12)0xffffffff'), tree.replace(BIND, 'wrong'), tree.replace(ACTION, 'wrong'), tree + tree):
            with self.assertRaises(RuntimeError): validate_apk_services(bad)

    def test_lock_activity_must_be_private_and_in_separate_task(self):
        for attribute, value in (("exported", "true"), ("taskAffinity", ""), ("turnScreenOn", "true"), ("directBootAware", "true")):
            manifest = copy.deepcopy(self.manifest)
            manifest.find("application/activity").set(ANDROID + attribute, value)
            with self.subTest(attribute=attribute), self.assertRaises(RuntimeError): validate_source(manifest)
        ET.SubElement(self.manifest.find("application/activity"), "intent-filter")
        with self.assertRaises(RuntimeError): validate_source(self.manifest)

    def test_compiled_lock_contract(self):
        tree = '''    E: activity (line=12)
      A: android:name(0x01010003)="dev.vitreo.launcher.lock.LockScreenActivity"
      A: android:taskAffinity(0x01010012)="dev.vitreo.launcher.lock"
      A: android:exported(0x01010010)=(type 0x12)0x0
      A: android:excludeFromRecents(0x01010017)=(type 0x12)0xffffffff
    E: activity (line=15)
'''
        validate_apk_lock_activity(tree)
        for bad in (tree.replace("0x12)0x0", "0x12)0xffffffff"), tree.replace("dev.vitreo.launcher.lock\"", "wrong\""), tree + tree):
            with self.assertRaises(RuntimeError): validate_apk_lock_activity(bad)

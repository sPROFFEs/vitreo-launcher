import sys
from pathlib import Path
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from device_smoke import tap_settings_row, PACKAGE

class SettingsNavigationTests(unittest.TestCase):
    def fixture(self):
        return ET.fromstring(f'''<hierarchy><node package="{PACKAGE}" clickable="true" bounds="[53,947][1027,1084]">
            <node text="Today &amp; News"/><node text="Widgets"/>
        </node><node package="{PACKAGE}" clickable="true" bounds="[53,2051][1027,2188]">
            <node text="Widgets"/>
        </node></hierarchy>''')

    def test_widget_detail_never_opens_today_instead_of_the_catalog(self):
        with patch("device_smoke.adb") as adb:
            tap_settings_row(self.fixture(), "Widgets")
            adb.assert_called_once_with("shell", "input", "tap", "540", "2119")

    def test_missing_or_duplicate_row_titles_fail_without_tapping(self):
        for value in ("Missing", "Widgets"):
            root = self.fixture()
            if value == "Widgets":
                root.append(ET.fromstring(f'<node package="{PACKAGE}" clickable="true" bounds="[0,0][100,100]"><node text="Widgets"/></node>'))
            with patch("device_smoke.adb") as adb, self.assertRaises(RuntimeError):
                tap_settings_row(root, value)
            adb.assert_not_called()

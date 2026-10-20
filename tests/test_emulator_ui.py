"""Emulator preflight must never suppress a product ANR or alter a personal device."""
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from device_smoke import prepare_emulator_home


class EmulatorUiTests(unittest.TestCase):
    def test_personal_device_is_rejected_before_any_mutation(self):
        with patch('device_smoke.adb', return_value='0') as device:
            with self.assertRaisesRegex(RuntimeError, 'disposable emulator'):
                prepare_emulator_home()
        self.assertEqual(device.call_args_list[0].args, ('shell', 'getprop', 'ro.kernel.qemu'))
        self.assertEqual(device.call_count, 1)

    def test_unknown_home_is_not_force_stopped(self):
        with patch('device_smoke.adb', side_effect=['1', 'other.launcher/.Home', 'other.launcher/.Home']) as device:
            with self.assertRaisesRegex(RuntimeError, 'No known stock'):
                prepare_emulator_home()
        self.assertFalse(any('force-stop' in call.args for call in device.call_args_list))

    def test_product_anr_after_stock_reset_fails_without_a_retry(self):
        component = 'com.google.android.apps.nexuslauncher/.NexusLauncherActivity'
        with patch('device_smoke.adb', side_effect=['1', component, '', 'Status: ok',
                'mCurrentFocus=Window{Application Not Responding: dev.vitreo.launcher.debug}']) as device:
            with self.assertRaisesRegex(RuntimeError, 'ANR blocks emulator UI'):
                prepare_emulator_home()
        stops = [call.args for call in device.call_args_list if 'force-stop' in call.args]
        self.assertEqual(stops, [('shell', 'am', 'force-stop', 'com.google.android.apps.nexuslauncher')])


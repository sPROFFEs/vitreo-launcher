import sys
import tempfile
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from instrumentation_results import write_junit
from device_smoke import passed_tests


def transcript(codes=(0, 0), count=None):
    count = len(codes) if count is None else count
    out = []
    for index, code in enumerate(codes):
        for status in (1, code):
            out += [f'INSTRUMENTATION_STATUS: class=example.Tests', f'INSTRUMENTATION_STATUS: test=case{index}',
                f'INSTRUMENTATION_STATUS: numtests={count}', 'INSTRUMENTATION_STATUS: stack=detail <escaped>',
                f'INSTRUMENTATION_STATUS_CODE: {status}']
    out += ['INSTRUMENTATION_RESULT: stream=', f'OK ({sum(c == 0 for c in codes)} tests)', 'INSTRUMENTATION_CODE: -1']
    return '\n'.join(out) + '\n'


class InstrumentationProtocolTests(unittest.TestCase):
    def test_complete_runner_results_produce_verified_junit(self):
        with tempfile.TemporaryDirectory() as folder:
            output = Path(folder) / 'TEST-run.xml'
            self.assertEqual(write_junit(transcript(), output), 2)
            self.assertEqual(passed_tests(Path(folder)), 2)

    def test_failure_error_skip_and_assumption_cannot_be_reported_as_passed(self):
        for status in (-1, -2, -3, -4):
            with self.subTest(status=status), tempfile.TemporaryDirectory() as folder:
                output = Path(folder) / 'TEST-run.xml'
                with self.assertRaises(RuntimeError): write_junit(transcript((0, status)), output)
                with self.assertRaises(RuntimeError): passed_tests(Path(folder))
                self.assertIn('detail &lt;escaped&gt;', output.read_text())

    def test_truncation_inconsistent_counts_and_false_success_are_rejected(self):
        for text in ['', 'OK (2 tests)\nINSTRUMENTATION_CODE: -1\n', transcript(count=3),
            transcript().replace('INSTRUMENTATION_CODE: -1', 'INSTRUMENTATION_CODE: 0'),
            transcript().replace('OK (2 tests)', 'OK (3 tests)'), transcript().replace('case1', 'case0')]:
            with self.subTest(text=text[-80:]), tempfile.TemporaryDirectory() as folder:
                with self.assertRaises(RuntimeError): write_junit(text, Path(folder) / 'TEST-run.xml')

    def test_a_finished_test_requires_an_actual_start_event(self):
        text = transcript((0,)).split('INSTRUMENTATION_STATUS_CODE: 1\n', 1)[1]
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaisesRegex(RuntimeError, 'without starting'):
                write_junit(text, Path(folder) / 'TEST-run.xml')

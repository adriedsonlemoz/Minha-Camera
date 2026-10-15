import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from check_android_resources import errors_in_android_string, check

class AndroidResourcePreflightTests(unittest.TestCase):
    def test_reject_unescaped_apostrophe(self):
        self.assertTrue(errors_in_android_string("marca d'água"))

    def test_allow_escaped_apostrophe(self):
        self.assertFalse(errors_in_android_string(r"marca d\'água"))

    def test_allow_escaped_newlines(self):
        self.assertFalse(errors_in_android_string(r"Primeira linha\nSegunda linha"))

    def test_reject_bad_unicode_escape(self):
        self.assertTrue(errors_in_android_string(r"teste \u12G4"))

    def test_reject_unknown_escape(self):
        self.assertTrue(errors_in_android_string(r"teste \a"))

    def test_project_resources_pass(self):
        count, errors = check(Path(__file__).resolve().parents[1])
        self.assertGreater(count, 10)
        self.assertEqual(errors, [])

if __name__ == '__main__':
    unittest.main()

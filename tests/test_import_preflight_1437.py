"""Regression for Build APK 37 (Android Intent constructor requires explicit import)."""
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
MODULE = ROOT / "scripts" / "check_kotlin_imports.py"
spec = importlib.util.spec_from_file_location("check_kotlin_imports", MODULE)
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


class IntentImportPreflightTest(unittest.TestCase):
    def test_missing_intent_import_detected(self):
        source = "package example\nfun x() { startActivity(Intent(this, X::class.java)) }\n"
        self.assertTrue(any("Intent" in problem for problem in checker.import_problems(source)))

    def test_valid_explicit_intent_import(self):
        source = "package example\nimport android.content.Intent\nfun x() { startActivity(Intent(this, X::class.java)) }\n"
        self.assertEqual([], checker.import_problems(source))

    def test_fully_qualified_intent(self):
        source = "package example\nfun x() { startActivity(android.content.Intent(this, X::class.java)) }\n"
        self.assertEqual([], checker.import_problems(source))

    def test_viewer_import_is_present(self):
        source = (ROOT / 'app/src/main/java/com/minhacamera/PhoneCameraViewerActivity.kt').read_text()
        self.assertIn('import android.content.Intent', source)
        self.assertEqual([], checker.import_problems(source))


if __name__ == '__main__':
    unittest.main()

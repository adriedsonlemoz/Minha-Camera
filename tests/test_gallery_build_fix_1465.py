"""Regressão: Build APK 64 falhava por minHeight em LinearLayout Kotlin."""
import importlib.util
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
SOURCE=(ROOT/'app/src/main/java/com/minhacamera/GalleryActivity.kt').read_text(encoding='utf-8')
spec=importlib.util.spec_from_file_location('check_minimum_size', ROOT/'scripts/check_kotlin_view_minimum_size.py')
checker=importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)

class GalleryBuild65Tests(unittest.TestCase):
    def test_minimum_height_uses_java_view_setter(self):
        self.assertIn('setMinimumHeight(dp(84))', SOURCE)
        self.assertNotIn('minHeight = dp(84)',SOURCE)
    def test_preflight_finds_old_bug(self):
        bug="val card = LinearLayout(this).apply {\n    gravity = Gravity.CENTER\n    minHeight = dp(84)\n    setPadding(1, 1, 1, 1)\n}"
        self.assertEqual(checker.find_invalid(bug),[(3,'minHeight')])
        self.assertEqual(checker.find_invalid(bug.replace('minHeight','minWidth')),[(3,'minWidth')])
    def test_preflight_allows_android_setter_and_textview(self):
        good="val card = LinearLayout(this).apply {\n    setMinimumHeight(dp(84))\n    addView(TextView(this).apply {\n        minHeight = 40\n    })\n}"
        self.assertEqual(checker.find_invalid(good),[])
    def test_preflight_scans_corrected_source(self):
        self.assertEqual(checker.find_invalid(SOURCE), [])
    def test_workflow_enforces_preflight(self):
        action=(ROOT/'.github/workflows/build-apk.yml').read_text()
        self.assertIn('python3 scripts/check_kotlin_view_minimum_size.py',action)
        self.assertIn('test_gallery_build_fix_1465.py',action)

if __name__=='__main__': unittest.main()

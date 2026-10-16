"""Regressão da falha compileReleaseKotlin do Build APK 62."""
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/minhacamera/GalleryActivity.kt"
SPEC = importlib.util.spec_from_file_location("check_argb", ROOT / "scripts/check_kotlin_argb_literals.py")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class Build62ColorFix(unittest.TestCase):
    def test_gallery_overlay_argb_is_explicit_int(self):
        kotlin = SOURCE.read_text(encoding="utf-8")
        self.assertIn("setBackgroundColor(0x990A1422.toInt())", kotlin)
        self.assertNotIn("setBackgroundColor(0x990A1422)", kotlin)
        self.assertEqual(MODULE.main(), 0)

    def test_unsigned_color_is_rejected_without_conversion(self):
        source = "setBackgroundColor(0x990A1422)\nsetTextColor(0xFFFFFFFF)"
        self.assertEqual(len(list(MODULE.find_unsafe_argb(source))), 2)
        self.assertEqual(list(MODULE.find_unsafe_argb("setBackgroundColor(0x990A1422.toInt())")), [])
        self.assertEqual(list(MODULE.find_unsafe_argb("setBackgroundColor(0x110A1422)")), [])

    def test_workflow_runs_preflight_before_gradle(self):
        workflow = (ROOT / ".github/workflows/build-apk.yml").read_text(encoding="utf-8")
        self.assertIn("python3 scripts/check_kotlin_argb_literals.py", workflow)
        self.assertLess(workflow.index("python3 scripts/check_kotlin_argb_literals.py"),
                        workflow.index("gradle testProtocol testReleaseUnitTest assembleRelease"))


if __name__ == "__main__":
    unittest.main()

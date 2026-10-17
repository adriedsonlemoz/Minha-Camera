"""Regressões: Build APK 66 falhava com `fillViewport` em HorizontalScrollView."""
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
GALLERY = (ROOT / "app/src/main/java/com/minhacamera/GalleryActivity.kt").read_text(encoding="utf-8")
SPEC = importlib.util.spec_from_file_location("check_kotlin_scroll_fill_viewport", ROOT / "scripts/check_kotlin_scroll_fill_viewport.py")
CHECKER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECKER)

class GalleryBuild67Tests(unittest.TestCase):
    def test_real_footer_uses_sdk_setter(self):
        self.assertIn('setFillViewport(true)', GALLERY)
        self.assertIn('val footerScroll = HorizontalScrollView(this).apply {', GALLERY)
        self.assertEqual(CHECKER.find_invalid(GALLERY), [])

    def test_preflight_detects_original_failure(self):
        old = 'val footerScroll = HorizontalScrollView(this).apply {\n    fillViewport = true\n}'
        self.assertEqual(CHECKER.find_invalid(old), [2])
        self.assertEqual(CHECKER.find_invalid(old.replace('fillViewport =', 'this.fillViewport =')), [2])

    def test_preflight_allows_supported_forms(self):
        good = 'val footerScroll = HorizontalScrollView(this).apply {\n    setFillViewport(true)\n    isFillViewport = true\n}'
        self.assertEqual(CHECKER.find_invalid(good), [])

    def test_workflow_runs_preflight(self):
        workflow = (ROOT / '.github/workflows/build-apk.yml').read_text(encoding='utf-8')
        self.assertIn('python3 scripts/check_kotlin_scroll_fill_viewport.py', workflow)
        self.assertIn('test_gallery_build_fix_1467.py', workflow)

    def test_source_keeps_actions_and_rename(self):
        for expected in ['footerAction(', 'HorizontalScrollView', 'promptRename()', 'subtleActionBackground()']:
            self.assertIn(expected, GALLERY)

if __name__ == '__main__':
    unittest.main()

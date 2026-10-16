"""Regressões da compilação Kotlin do receptor após Build APK 55."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/minhacamera/PhoneCameraViewerActivity.kt"


class ViewerBuildFix1456(unittest.TestCase):
    def test_all_referenced_ids_are_declared(self):
        from sys import path
        path.insert(0, str(ROOT / "scripts"))
        from check_viewer_bindings import missing_view_ids
        self.assertEqual(missing_view_ids(), [])

    def test_pip_exit_clears_existing_fullscreen_dismissal_state(self):
        source = SOURCE.read_text()
        self.assertIn("if (!inPictureInPictureMode) {\n            dismissedFullscreenOrientation = null", source)
        self.assertNotIn("landscapeDismissedByUser", source)

    def test_fullscreen_buttons_match_existing_layout(self):
        source = SOURCE.read_text()
        for view_id in ("viewerFullBack", "viewerFullRecord", "viewerFullPhoto", "viewerFullRotate", "viewerFullFit"):
            self.assertIn(f"R.id.{view_id}", source)
        self.assertNotIn("R.id.viewerFullPip", source)


if __name__ == "__main__":
    unittest.main()

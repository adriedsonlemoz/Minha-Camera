"""Smoke tests de integração estática da tela retrovisor 1.4.24."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
PKG = ROOT / "app/src/main/java/com/minhacamera"
RES = ROOT / "app/src/main/res"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


class RearviewTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.code = (PKG / "PhoneCameraViewerActivity.kt").read_text()
        cls.layout = ET.parse(RES / "layout/activity_phone_viewer.xml").getroot()
        cls.strings = (RES / "values/strings.xml").read_text()

    def test_five_controls(self):
        ids = ["phoneRetry", "phoneViewerFit", "phoneViewerMirror", "phoneViewerFocus", "phoneViewerMore"]
        controls = next(e for e in self.layout.iter() if e.attrib.get(ANDROID_NS + "id") == "@+id/phoneViewerControls")
        found = [e.attrib.get(ANDROID_NS + "id") for e in controls]
        self.assertEqual(found, ["@+id/" + x for x in ids])

    def test_mirror_is_view_transform_not_reencode(self):
        self.assertIn("image.scaleX = if (mirrorMode) -1f else 1f", self.code)
        self.assertIn('putBoolean("mirror", mirrorMode)', self.code)
        self.assertIn('getBoolean("mirror", false)', self.code)
        self.assertNotIn("Bitmap.createBitmap(image", self.code)

    def test_focus_restorable_by_tap(self):
        self.assertIn('previewContainer.setOnClickListener { toggleFocusMode() }', self.code)
        self.assertIn('controls.visibility = if (focusMode) View.GONE else View.VISIBLE', self.code)
        self.assertIn("previewContainer.layoutParams = lp", self.code)

    def test_stale_image_cleared_on_disconnect(self):
        self.assertIn("image.setImageDrawable(null)", self.code)
        self.assertIn('status.setText(R.string.phone_reconnecting)', self.code)

    def test_no_new_permissions_or_dependencies(self):
        gradle = (ROOT / 'app/build.gradle.kts').read_text()
        self.assertIn('versionName = "1.4.25"', gradle)
        self.assertIn('versionCode = 35', gradle)
        self.assertNotIn('implementation("androidx.camera', gradle)

    def test_vertical_layout_and_labels(self):
        self.assertIn('ActivityInfo.SCREEN_ORIENTATION_PORTRAIT', self.code)
        for name in ["phone_view_mirror", "phone_view_focus", "phone_view_focus_hint", "phone_view_retry_short"]:
            self.assertIn('name="' + name + '"', self.strings)

if __name__ == "__main__":
    unittest.main()

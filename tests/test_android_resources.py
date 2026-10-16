import sys
import unittest
import tempfile
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

    def test_reject_material_button_app_insets(self):
        with tempfile.TemporaryDirectory() as tmp:
            layout_dir = Path(tmp) / 'app/src/main/res/layout'
            layout_dir.mkdir(parents=True)
            (layout_dir / 'item_camera.xml').write_text(
                '<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" '
                'xmlns:app="http://schemas.android.com/apk/res-auto">'
                '<com.google.android.material.button.MaterialButton '
                'android:id="@+id/btnOpen" app:insetLeft="0dp" app:insetTop="0dp" />'
                '</LinearLayout>', encoding='utf-8'
            )
            _, errors = check(Path(tmp))
            self.assertEqual(len(errors), 2)
            self.assertTrue(any('app:insetLeft' in e for e in errors))
            self.assertTrue(any('app:insetTop' in e for e in errors))

    def test_device_buttons_have_no_invalid_insets(self):
        layout = Path(__file__).resolve().parents[1] / 'app/src/main/res/layout/item_camera.xml'
        self.assertEqual(layout.read_text().count('<com.google.android.material.button.MaterialButton'), 4)
        self.assertNotIn('app:insetLeft=', layout.read_text())
        self.assertNotIn('app:insetRight=', layout.read_text())
        self.assertNotIn('app:insetTop=', layout.read_text())
        self.assertNotIn('app:insetBottom=', layout.read_text())

    def test_project_resources_pass(self):
        count, errors = check(Path(__file__).resolve().parents[1])
        self.assertGreater(count, 10)
        self.assertEqual(errors, [])

if __name__ == '__main__':
    unittest.main()

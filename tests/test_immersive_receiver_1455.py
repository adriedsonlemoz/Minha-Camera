"""Requisitos estáticos e geometria do visualizador 1.4.55."""
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
P = Path(__file__).resolve().parents[1]
PKG = P / 'app/src/main/java/com/minhacamera'
ANDROID = '{http://schemas.android.com/apk/res/android}'

class ReceiverUi1455(unittest.TestCase):
    def test_rotations_and_save(self):
        viewer = (PKG/'PhoneCameraViewerActivity.kt').read_text()
        transform = (PKG/'PhoneViewerImageView.kt').read_text()
        self.assertIn('(rotationDegrees + 90) % 360', viewer)
        self.assertIn('putInt("rotation_degrees", rotationDegrees)', viewer)
        self.assertIn('getInt("rotation_degrees", 0)', viewer)
        self.assertIn('transform.postRotate(videoDegrees.toFloat())', transform)
        self.assertIn('if (fill) maxOf(', transform)
        self.assertIn('else minOf(', transform)
        self.assertNotIn('Bitmap.createBitmap(', transform)

    def test_fullscreen_landscape_only_and_buttons(self):
        viewer = (PKG/'PhoneCameraViewerActivity.kt').read_text()
        self.assertIn('orientation == Configuration.ORIENTATION_LANDSCAPE', viewer)
        self.assertIn('if (!landscape)', viewer)
        self.assertNotIn('orientation == Configuration.ORIENTATION_LANDSCAPE ||', viewer)
        self.assertIn('dismissedFullscreenOrientation = resources.configuration.orientation', viewer)
        self.assertIn('previewContainer.post { updateVideoRotation() }', viewer)
        root = ET.parse(P/'app/src/main/res/layout/activity_phone_viewer.xml').getroot()
        ids = {v.get(ANDROID+'id'): v for v in root.iter()}
        for id in ('viewerFullBack','viewerFullRecord','viewerFullPhoto','viewerFullRotate','viewerFullFit'):
            button = ids['@+id/'+id]
            self.assertEqual(button.get(ANDROID+'layout_height'), '48dp')

    def test_compact_controls_and_icons(self):
        root = ET.parse(P/'app/src/main/res/layout/activity_phone_viewer.xml').getroot()
        controls = next(v for v in root.iter() if v.get(ANDROID+'id')=='@+id/phoneViewerControls')
        tiles = list(controls)
        self.assertGreaterEqual(len(tiles), 10)
        self.assertTrue(all(v.get(ANDROID+'layout_height')=='66dp' for v in tiles))
        chooser = (PKG/'PhoneViewerChoiceDialog.kt').read_text()
        self.assertIn('row.addView(ImageView(', chooser)
        self.assertIn('onChoose(index)', chooser)

    def test_remote_diagnostic_saf(self):
        viewer = (PKG/'PhoneCameraViewerActivity.kt').read_text()
        self.assertIn('ActivityResultContracts.CreateDocument("text/plain")', viewer)
        self.assertIn('sanitizeDiagnostic(report)', viewer)
        self.assertIn('CameraReportDialog(this,', viewer)
        self.assertIn('openOutputStream(uri)', viewer)

if __name__ == '__main__': unittest.main()

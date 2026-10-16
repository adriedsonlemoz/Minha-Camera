from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/com/minhacamera"

class AutoLandscapeFullscreen1447(unittest.TestCase):
    def test_auto_landscape_and_portrait_restoration(self):
        source = (SRC / "PhoneCameraViewerActivity.kt").read_text()
        for needle in (
            'private fun isAutomaticViewerRotation()',
            'PhoneOrientationMode.AUTO',
            'Configuration.ORIENTATION_LANDSCAPE',
            'private fun syncLandscapeFullscreen()',
            'landscapeDismissedByUser',
            'if (landscapeFullscreen && focusMode)',
            'focusMode = false',
            'focusMode = true',
            'syncLandscapeFullscreen()'
        ):
            self.assertIn(needle, source)
        self.assertGreaterEqual(source.count('syncLandscapeFullscreen()'), 5)
        self.assertIn('override fun onConfigurationChanged(newConfig: Configuration)', source)
        self.assertIn('override fun onPictureInPictureModeChanged(', source)
        self.assertIn('fullscreenHost.addView(previewContainer, 0, FrameLayout.LayoutParams(-1, -1))', source)
        self.assertIn('normalPreviewParent.addView(previewContainer, previewIndex,', source)

    def test_controls_remain_overlay_and_transparent(self):
        xml = (ROOT / 'app/src/main/res/layout/activity_phone_viewer.xml').read_text()
        bg = (ROOT / 'app/src/main/res/drawable/bg_viewer_full_controls.xml').read_text()
        self.assertIn('viewerFullscreenControls', xml)
        for name in ('viewerFullBack','viewerFullRecord','viewerFullPhoto','viewerFullRotate','viewerFullPip'):
            self.assertIn(name, xml)
        self.assertIn('#660B1420', bg)

    def test_correct_phone_orientation_convention(self):
        mode = (SRC / 'PhoneOrientationMode.kt').read_text()
        self.assertIn('(sensorMount + deviceDegrees) % 360', mode)
        self.assertIn('(sensorMount - deviceDegrees + 360) % 360', mode)
        self.assertIn('fun fromDisplayDegrees', mode)
        for name in ('PhoneCameraActivity.kt', 'PhoneCameraBackgroundService.kt'):
            self.assertIn('PhoneOrientationMode.fromDisplayDegrees(', (SRC / name).read_text())

if __name__ == '__main__':
    unittest.main()

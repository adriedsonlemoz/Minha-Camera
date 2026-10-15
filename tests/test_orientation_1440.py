from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT/'app/src/main/java/com/minhacamera'

class Orientation1440(unittest.TestCase):
    def test_modes_and_stabilizer(self):
        policy=(SRC/'PhoneOrientationMode.kt').read_text()
        for part in ('AUTO = "auto"', 'PORTRAIT = "portrait"', 'LANDSCAPE = "landscape"',
                     'fun deviceDegrees', 'fun frameDegrees', 'class PhoneOrientationStabilizer',
                     'settleMs: Long = 800L'):
            self.assertIn(part, policy)
    def test_activity(self):
        body=(SRC/'PhoneCameraActivity.kt').read_text()
        for part in ('orientationSensor.enable()', 'orientationSensor.disable()',
                     'PhoneOrientationMode.deviceDegrees', 'if (recordingRequested) return',
                     'chooseOrientationMode()', 'moreOrientationRow', 'updateOrientation()'):
            self.assertIn(part, body)
    def test_service_recording_safety(self):
        body=(SRC/'PhoneCameraBackgroundService.kt').read_text()
        for part in ('orientationSensor.enable()', 'orientationSensor.disable()',
                     'if (recording || closing || switching || takingPhoto || !running) return',
                     'val rotationForFrame = frameRotationDegrees',
                     'remoteResolution = "${width}x${height}"',
                     'ACTION_ORIENTATION', 'refreshFrameRotation()', 'server?.clearAvc()'):
            self.assertIn(part, body)
    def test_viewer_and_resource(self):
        viewer=(SRC/'PhoneCameraViewerActivity.kt').read_text()
        dialog=(ROOT/'app/src/main/res/layout/dialog_phone_more.xml').read_text()
        self.assertIn('SCREEN_ORIENTATION_UNSPECIFIED', viewer)
        self.assertIn('chooseViewerOrientation()', viewer)
        self.assertIn('SCREEN_ORIENTATION_LANDSCAPE', viewer)
        self.assertIn('refreshPreviewOrientation()', viewer)
        self.assertIn('moreOrientationRow', dialog)

if __name__ == '__main__': unittest.main()

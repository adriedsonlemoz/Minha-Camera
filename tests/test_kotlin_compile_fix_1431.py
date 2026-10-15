"""Regressões estruturais dos quatro erros :app:compileReleaseKotlin do Build APK 31."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT/'app/src/main/java/com/minhacamera'

class Build31FixTests(unittest.TestCase):
    def test_callback_does_not_capture_initializing_session(self):
        code = (SRC/'PhoneCameraBackgroundService.kt').read_text()
        self.assertNotIn('mainHandler.post { reconfigure(session, selectedFacing, quality) }', code)
        self.assertNotIn('mainHandler.post { reconfigure(session, desired, requestedQuality) }', code)
        self.assertIn('server?.let { active -> reconfigure(active, selectedFacing, quality) }', code)
        self.assertIn('server?.let { active -> reconfigure(active, desired, requestedQuality) }', code)
        self.assertIn('if (server !== session || closing || switching || recording || takingPhoto) return', code)
    def test_nullable_remote_controls_use_default_int(self):
        code = (SRC/'PhoneCameraViewerActivity.kt').read_text()
        self.assertIn('options.indexOf(remoteQuality ?: -1)', code)
        self.assertIn('options.indexOf(remoteFps ?: -1)', code)
        self.assertNotIn('options.indexOf(remoteQuality)', code)
        self.assertNotIn('options.indexOf(remoteFps)', code)

if __name__ == '__main__': unittest.main()

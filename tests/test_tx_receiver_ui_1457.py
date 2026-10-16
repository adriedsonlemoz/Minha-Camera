"""Regressões da interface 1.4.57, incluindo rotação, ações e segurança."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET
P = Path(__file__).resolve().parents[1]
S = P/'app/src/main/java/com/minhacamera'
R = P/'app/src/main/res/layout'
A = '{http://schemas.android.com/apk/res/android}'

class Ui1457(unittest.TestCase):
    def test_auto_fullscreen_is_landscape_only(self):
        viewer = (S/'PhoneCameraViewerActivity.kt').read_text()
        self.assertIn('val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE',viewer)
        self.assertIn('if (focusMode && !focusManuallyRequested)',viewer)
        tx = (S/'PhoneCameraActivity.kt').read_text()
        self.assertIn('orientation == Configuration.ORIENTATION_LANDSCAPE',tx)
        self.assertIn('if (txFull) setTransmitterFullscreen(false)',tx)
    def test_controls_and_pause(self):
        tx = (S/'PhoneCameraActivity.kt').read_text()
        self.assertIn('if (!transmissionPaused) {', tx)
        self.assertIn('server?.clearFrame()',tx)
        self.assertIn('selectedStreamCodec() == "mjpeg"',tx)
        x = ET.parse(R/'activity_phone_camera.xml').getroot()
        ids = {v.get(A+'id') for v in x.iter()}
        for name in ('phoneTransmissionToggle','phoneTransmissionPause','phoneTxFullBack','phoneTxFullLock','phoneTxFullscreenUnlock','phoneTxPerformance','phoneVideoBox'):
            self.assertIn('@+id/'+name,ids)
        self.assertTrue(any(v.get(A+'id')=='@+id/phoneTxVideoActions' for v in x.iter()))
        self.assertTrue(all(v.get(A+'layout_height')=='66dp' for v in x.iter() if v.get(A+'background')=='@drawable/bg_tx_tile_compact'))
    def test_lock_and_safe_export(self):
        tx = (S/'PhoneCameraActivity.kt').read_text()
        viewer = (S/'PhoneCameraViewerActivity.kt').read_text()
        for code in (tx,viewer):
            self.assertIn('postDelayed(',code)
            self.assertIn('2000L',code)
            self.assertIn('MotionEvent.ACTION_CANCEL',code)
        self.assertIn('ActivityResultContracts.CreateDocument("text/plain")',tx)
        self.assertIn('PhoneViewerChoiceDialog.show(',tx)
        receiver = ET.parse(R/'activity_phone_viewer.xml').getroot()
        ids = {v.get(A+'id') for v in receiver.iter()}
        for name in ('viewerFullLock','viewerFullUnlock','viewerFullMirror','viewerFullSettings'):
            self.assertIn('@+id/'+name,ids)

if __name__=='__main__': unittest.main()

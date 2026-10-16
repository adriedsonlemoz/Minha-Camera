"""Regressão de layout e do repasse de câmera 1.4.59.
   Comportamento físico exige verificação em aparelho; não afirmar que esses testes provam isso.
"""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'app/src/main/java/com/minhacamera'
A='{http://schemas.android.com/apk/res/android}'

class HandoffAndZoom59(unittest.TestCase):
    def test_headless_start_does_not_prepare_egl_on_main(self):
        source=(SRC/'PhoneCameraBackgroundService.kt').read_text()
        self.assertIn('Thread({', source)
        self.assertIn('PhoneHeadlessPreview.create(640, 480)',source)
        self.assertIn('startPreparedCapture(facing, prepared, generation)',source)
        self.assertIn('if (attempt < 2',source)
        self.assertIn('pendingCameraStart',source)
        self.assertIn('X-Zoom-', (SRC/'PhoneCameraServer.kt').read_text())

    def test_socket_rebind_safeguard(self):
        source=(SRC/'PhoneCameraServer.kt').read_text()
        self.assertIn('ServerSocket().apply',source)
        self.assertLess(source.index('reuseAddress = true'),source.index('bind(java.net.InetSocketAddress'))

    def test_periodic_mjpeg_capabilities(self):
        source=(SRC/'PhoneCameraViewerActivity.kt').read_text()
        self.assertIn('private val mjpegStatusTick',source)
        self.assertIn('statusHandler.postDelayed(this, 2500L)',source)
        self.assertIn('if (snapshot.codec == "mjpeg") applyRemoteStatus(snapshot)',source)
        self.assertIn('statusHandler.removeCallbacks(mjpegStatusTick)',source)

    def test_zoom_buttons_touch_and_local_fallback(self):
        root=ET.parse(ROOT/'app/src/main/res/layout/activity_phone_viewer.xml').getroot()
        ids={node.attrib.get(A+'id','').replace('@+id/','') for node in root.iter()}
        for key in ['viewerZoomMinus','viewerZoomReset','viewerZoomPlus',
                    'viewerFullZoomMinus','viewerFullZoomReset','viewerFullZoomPlus']:
            self.assertIn(key,ids)
        source=(SRC/'PhoneCameraViewerActivity.kt').read_text()
        self.assertIn('localZoomFactor =',source)
        self.assertIn('"Zoom local"',source)
        self.assertIn('sendRemoteZoom((current + direction * 10)',source)
        self.assertIn('1f + slider.progress / 50f',source)

    def test_fullscreen_only_landscape_remains(self):
        viewer=(SRC/'PhoneCameraViewerActivity.kt').read_text()
        sender=(SRC/'PhoneCameraActivity.kt').read_text()
        self.assertIn('if (!landscape)',viewer)
        self.assertIn('if (orientation == Configuration.ORIENTATION_LANDSCAPE)',sender)
        self.assertIn('if (txFull) setTransmitterFullscreen(false)',sender)

if __name__ == '__main__': unittest.main()

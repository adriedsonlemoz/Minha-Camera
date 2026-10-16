"""Regras de interface e zoom remoto da versão 1.4.58."""
from pathlib import Path
import unittest, xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
LAY=ROOT/'app/src/main/res/layout'
KOT=ROOT/'app/src/main/java/com/minhacamera'
A='{http://schemas.android.com/apk/res/android}'

def find(root, vid):
    return next(v for v in root.iter() if v.get(A+'id') == '@+id/'+vid)

class ZoomAndPreview58(unittest.TestCase):
    def test_normal_actions_away_from_texture(self):
        root=ET.parse(LAY/'activity_phone_camera.xml').getroot()
        video=find(root,'phoneVideoBox'); panel=find(root,'phoneTxVideoActions')
        self.assertFalse(any(v is panel for v in video.iter()))
        content=find(root,'phoneTxContent')
        self.assertEqual(list(content).index(panel),list(content).index(video)+1)
        self.assertEqual(panel.get(A+'background'),'@drawable/bg_tx_actions_panel')
    def test_fullscreen_controls_outside_video(self):
        root=ET.parse(LAY/'activity_phone_camera.xml').getroot()
        bar=find(root,'phoneTxFullscreenControls')
        self.assertEqual(bar.get(A+'background'),'@drawable/bg_tx_full_actions')
        self.assertEqual(bar.get(A+'layout_marginBottom'),'0dp')
        source=(KOT/'PhoneCameraActivity.kt').read_text()
        self.assertIn('bottomMargin = txDp(58)',source)
        self.assertIn('bottomMargin = 0',source)
        self.assertIn('txVideoBox.layoutParams.apply { height = txScroll.height }',source)
    def test_real_zoom_and_remote_controls(self):
        source=(KOT/'PhoneCameraActivity.kt').read_text()
        self.assertIn('params.isZoomSupported',source)
        self.assertIn('params.zoom = step',source)
        self.assertIn('CountDownLatch(1)',source)
        self.assertIn('changeZoom = { percent -> setZoomFromRemote(percent) }',source)
        service=(KOT/'PhoneCameraBackgroundService.kt').read_text()
        self.assertIn('changeZoom = { percent -> setServiceZoom(percent) }',service)
        self.assertIn('params.zoom = step',service)
        self.assertIn('zoomStatus = { if (running && liveCodec == "mjpeg"',service)
        server=(KOT/'PhoneCameraServer.kt').read_text()
        self.assertIn('X-Zoom-Supported:',server)
        self.assertIn('X-Zoom-Percent:',server)
        self.assertIn('changeZoom?.invoke(zoomPercent)',server)
        viewer=ET.parse(LAY/'activity_phone_viewer.xml').getroot()
        find(viewer,'viewerRemoteZoom');find(viewer,'viewerFullZoom')
        receiver=(KOT/'PhoneCameraViewerActivity.kt').read_text()
        self.assertIn('zoomCommandUrl(',receiver)
        self.assertIn('private fun chooseRemoteZoom()',receiver)
    def test_unsupported_and_sanitized(self):
        urls=(KOT/'PhoneCameraUrls.kt').read_text()
        self.assertIn('if (percent in 0..100)',urls)
        self.assertIn('controlUrl(input, "zoom/$percent")',urls)
        viewer=(KOT/'PhoneCameraViewerActivity.kt').read_text()
        self.assertIn('if (current == null)',viewer)
        self.assertIn('"Indisponível"',viewer)

if __name__=='__main__':unittest.main()

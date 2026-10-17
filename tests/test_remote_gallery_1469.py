from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
K=ROOT/'app/src/main/java/com/minhacamera'
class Version1469(unittest.TestCase):
    def test_remote_media_with_bounded_previews(self):
        s=(K/'RemoteMultimediaActivity.kt').read_text()
        self.assertIn('newFixedThreadPool(2)',s)
        self.assertIn('loadThumbnail(thumb',s)
        self.assertIn('loadVideoThumbnail(thumb',s)
        self.assertIn('size > 8_000_000L',s)
        self.assertIn('MediaMetadataRetriever',s)
        self.assertIn('Environment.DIRECTORY_PICTURES',s)
        self.assertIn('Environment.DIRECTORY_MOVIES',s)
        self.assertIn('"/MinhaCamera"',s)
        self.assertIn('orientJpeg',s)
    def test_zoom_actual_limit_and_jpeg_orientation(self):
        server=(K/'PhoneCameraServer.kt').read_text()
        service=(K/'PhoneCameraBackgroundService.kt').read_text()
        receiver=(K/'PhoneCameraViewerActivity.kt').read_text()
        assert 'X-Zoom-Max-Ratio-100' in server
        assert 'zoomMaxRatioStatus' in service
        assert 'params.setRotation' in service
        assert 'params.setRotation' in (K/'PhoneCameraActivity.kt').read_text()
        assert 'remoteZoomMaxRatio100' in receiver
        assert 'Restaurar 1×' in receiver
        assert 'filter { it <= maxRatio' in receiver
    def test_layout_fullscreen_and_performance(self):
        root=ET.parse(ROOT/'app/src/main/res/layout/activity_phone_viewer.xml').getroot()
        a='{http://schemas.android.com/apk/res/android}'
        ids=[n.get(a+'id','') for n in root.iter()]
        self.assertIn('@+id/viewerFullscreenExitTop',ids)
        s=(K/'PhoneCameraViewerActivity.kt').read_text()
        self.assertIn('performance.text = "${measured} FPS',s)
        self.assertIn('"${measured} / ${remoteFps',s)
if __name__ == '__main__':unittest.main()

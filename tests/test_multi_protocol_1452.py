from pathlib import Path
import unittest
R = Path(__file__).resolve().parents[1]
P = (R/'app/src/main/java/com/minhacamera/PlayerActivity.kt').read_text()
M = (R/'app/src/main/java/com/minhacamera/MainActivity.kt').read_text()
S = (R/'app/src/main/java/com/minhacamera/ScanActivity.kt').read_text()
K = (R/'app/src/main/java/com/minhacamera/CameraStreamProtocol.kt').read_text()
class MultiProtocol(unittest.TestCase):
    def test_player_no_longer_filters_only_rtsp(self):
        self.assertIn('CameraStreamProtocol.isSupported(it)', P)
        self.assertIn('CameraStreamProtocol.nextAttempt(index, rtspTransport, urls)', P)
        self.assertIn('CameraStreamProtocol.isRtsp(url)', P)
    def test_http_is_not_assumed_phone(self):
        self.assertIn('PhoneCameraUrls.normalize(camera.url) != null', M)
    def test_direct_urls_for_ip_cameras(self):
        self.assertIn('CameraStreamProtocol.isSupported(ipText)', S)
        self.assertIn('CameraStreamProtocol.isSupported(target)', M)
        self.assertIn('StreamKind.HLS', K)
        self.assertIn('StreamKind.RTMP', K)
    def test_manual_rtsp_can_use_separate_credentials(self):
        form = (R/'app/src/main/java/com/minhacamera/CameraFormUi.kt').read_text()
        self.assertIn('fun attachRtspCredentials(', form)
        self.assertIn('CameraFormUi.attachRtspCredentials(ipText, user, password)', S)
        self.assertIn('CameraFormUi.attachRtspCredentials(target,', M)
    def test_no_false_onvif_player(self):
        self.assertNotIn('"onvif" to StreamKind', K)
        self.assertNotIn('"webrtc" to StreamKind', K)
    def test_rtsp_auth_probe_requires_video(self):
        src=(R/'app/src/main/java/com/minhacamera/RtspProbe.kt').read_text()
        self.assertIn('r2.sdp.contains("m=video"', src)
        self.assertIn('r1.sdp.contains("m=video"', src)
    def test_scan_does_not_probe_detected_http_as_rtsp(self):
        self.assertIn('f.services[it] == null', S)
        self.assertIn('80, 8080, 5000', S)
    def test_path_tokens_are_masked(self):
        self.assertIn('safeReportPath(', P)
        self.assertIn('caminho protegido', P)
    def test_only_rtsp_gets_rtsp_probe(self):
        self.assertIn('urls.filter { CameraStreamProtocol.isRtsp(it) }.take(2)', P)
if __name__ == '__main__': unittest.main()

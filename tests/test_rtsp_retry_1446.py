from pathlib import Path
import unittest
R = Path(__file__).resolve().parents[1]
P = (R/'app/src/main/java/com/minhacamera/PlayerActivity.kt').read_text()
L = (R/'app/src/main/res/layout/activity_player.xml').read_text()
S = (R/'app/src/main/res/values/strings.xml').read_text()

class RtspRetryTests(unittest.TestCase):
    def test_per_url_tcp_then_udp(self):
        self.assertIn('RtspPlaybackPolicy.next(', P)
        self.assertIn('rtspTransport == RtspTransport.TCP', P)
        self.assertIn('coreOptions.add("--rtsp-tcp")', P)
        self.assertIn('media.addOption(":rtsp-tcp")', P)
        self.assertNotIn('arrayListOf("--rtsp-tcp",', P)
    def test_uses_local_wifi(self):
        self.assertIn('NetUtil.bindToWifi(applicationContext)', P)
    def test_diagnosis_does_not_expose_credentials(self):
        self.assertIn('RtspProbe.probe(', P)
        self.assertIn('rtsp_diagnostic_auth', P)
        self.assertIn('rtsp_diagnostic_path', P)
        self.assertNotIn('errorDetail.text = probe.debug', P)
        self.assertNotIn('lines.joinToString("\n") + address', P)
    def test_copy_diagnosis_and_ui(self):
        self.assertIn('btnCopyRtspDiagnostic', P)
        self.assertIn('btnCopyRtspDiagnostic', L)
        self.assertIn('rtsp_diagnostic_describe_ok', S)
if __name__=='__main__': unittest.main()

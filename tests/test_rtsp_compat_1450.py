from pathlib import Path
import unittest

R = Path(__file__).resolve().parents[1]
P = (R / "app/src/main/java/com/minhacamera/PlayerActivity.kt").read_text()
N = (R / "app/src/main/java/com/minhacamera/NetUtil.kt").read_text()
D = (R / "app/src/main/java/com/minhacamera/RtspServiceDetector.kt").read_text()
W = (R / "app/src/main/java/com/minhacamera/WelcomeActivity.kt").read_text()

class RtspCompatibility1450(unittest.TestCase):
    def test_external_vlc_requires_consent(self):
        self.assertIn("private fun testExternalVlc()", P)
        self.assertIn('.setPositiveButton("Abrir VLC")', P)
        self.assertIn('setPackage("org.videolan.vlc")', P)
        self.assertIn("hasCredentials", P)
        self.assertIn("R.string.cancelar", P)
    def test_network_route_is_manual_and_defaults_to_legacy(self):
        self.assertIn("private var useWifiBinding = false", P)
        self.assertIn('getBoolean("bind_wifi", false)', P)
        self.assertIn("private fun toggleRtspRoute()", P)
        self.assertIn("bindProcessToNetwork(null)", N)
        self.assertIn("selectRtspRoute()", P)
    def test_diagnostic_separates_options_describe_and_vlc(self):
        self.assertIn("statusCode: Int?", D)
        self.assertIn("OPTIONS:", D)
        self.assertIn("probe.status", P)
        for text in ("decodedVideo", "displayedPictures", "readBytes", "demuxReadBytes"):
            self.assertIn(text, P)
        self.assertIn("lastVlcStats", P)
        self.assertIn("maskCredentials(report)", P)
    def test_welcome_only_this_version(self):
        for text in ("Mais formatos de vídeo", "Busca de serviços ampliada", "RTSP com diagnóstico mais rigoroso"):
            self.assertIn(text, W)
        for old_title in ("Teste no VLC independente", "Duas rotas de rede", "Diagnóstico RTSP aprofundado"):
            self.assertNotIn(old_title, W)
        self.assertNotIn("Novidades centralizadas", W)

if __name__ == "__main__": unittest.main()

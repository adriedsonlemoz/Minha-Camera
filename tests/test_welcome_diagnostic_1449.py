from pathlib import Path
import unittest
R = Path(__file__).resolve().parents[1]
W = (R / 'app/src/main/java/com/minhacamera/WelcomeActivity.kt').read_text()
D = (R / 'app/src/main/java/com/minhacamera/CameraReportDialog.kt').read_text()
P = (R / 'app/src/main/java/com/minhacamera/PlayerActivity.kt').read_text()

class NewDesignAndRtspDiagnostic(unittest.TestCase):
    def test_welcome_centers_without_removing_scroll(self):
        self.assertIn('gravity = Gravity.CENTER_VERTICAL', W)
        self.assertIn('isFillViewport = true', W)
        self.assertIn('ScrollView(this)', W)
    def test_buttons_have_distinct_semantic_contrast(self):
        self.assertIn('action("Fechar", primary = false, neutral = true)', D)
        self.assertIn('action("Copiar", primary = true)', D)
        self.assertIn('action("Exportar", primary = false)', D)
        for color in ('R.color.app_text','R.color.brand_primary','R.color.brand_on_primary'):
            self.assertIn(color, D)
        self.assertIn('ColorStateList.valueOf', D)
    def test_open_diagnostic_updates_after_async_probe(self):
        self.assertIn('private var rtspAnalysis', P)
        self.assertIn('lines.add(rtspAnalysis)', P)
        self.assertIn('rtspAnalysis = lines.joinToString("\\n")', P)
        self.assertIn('diagnosticDialog?.takeIf { it.isShowing }?.update(reportSnapshot())', P)
        self.assertNotIn('lines.add(if (::errorDetail.isInitialized)', P)
    def test_authentication_still_redacted(self):
        self.assertIn('maskCredentials(report)', P)
        self.assertIn('rtsp://[endereço oculto]', P)
if __name__ == '__main__': unittest.main()

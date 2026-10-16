from pathlib import Path
import unittest
R = Path(__file__).resolve().parents[1]
P = (R/'app/src/main/java/com/minhacamera/PlayerActivity.kt').read_text()
Q = (R/'app/src/main/java/com/minhacamera/RtspProbe.kt').read_text()
T = (R/'tests/RtspSessionSmoke.kt').read_text()
class SessionDiagnosis1453(unittest.TestCase):
    def test_probe_distinguishes_describe_setup_play_and_packet(self):
        for item in ('fun probeSetupPlay(', '"SETUP"', '"PLAY"', '"RTP/TCP"',
                     '"RTP/AVP/TCP;unicast;interleaved=0-1"', 'videoControl(', 'resolveControl(',
                     'result.code == 461' if False else 'setup.code == 461'):
            self.assertIn(item,Q)
    def test_local_socket_cases(self):
        for item in ('ServerSocket(0)', 'scenario(200, reconnect)', 'scenario(461, reconnect)', 'Authorization: Digest',
                     'random-session'):
            self.assertIn(item,T)
    def test_player_auth_fallback_preserves_original_url(self):
        for text in ('private var rtspOptionsOnly = false',
                     'rtspOptionsOnly && !encodedInfo.isNullOrBlank()',
                     "parsedRtsp.encodedAuthority.orEmpty().substringAfterLast('@')",
                     'media.addOption(":rtsp-user=$user")', 'media.addOption(":rtsp-pwd=$pass")',
                     'retryAuth -> { rtspOptionsOnly = true }', 'next != null -> {',
                     'rtspOptionsOnly = false'):
            self.assertIn(text,P)
    def test_report_contains_session_stage_not_secrets(self):
        self.assertIn('RtspProbe.probeSetupPlay(host, port, user, password, path)', P)
        self.assertIn('"; sequência TCP: ${result.stage} "',P)
        self.assertIn('maskCredentials(lines.joinToString',P)
        self.assertNotIn('rtsp://admin:',P)
if __name__=='__main__': unittest.main()

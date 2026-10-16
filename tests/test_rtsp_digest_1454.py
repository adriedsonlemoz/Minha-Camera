from pathlib import Path
import unittest
R = Path(__file__).resolve().parents[1]
Q = (R/'app/src/main/java/com/minhacamera/RtspProbe.kt').read_text()
P = (R/'app/src/main/java/com/minhacamera/PlayerActivity.kt').read_text()
T = (R/'tests/RtspSessionSmoke.kt').read_text()
class Digest1454(unittest.TestCase):
    def test_digest_binds_to_actual_rtsp_method(self):
        self.assertIn('val ha2 = md5("$method:$digestUri")', Q)
        self.assertIn('method = "SETUP"', Q)
        self.assertIn('method = "PLAY"', Q)
        self.assertIn('method = "DESCRIBE"', Q)
        self.assertIn('nonceCount = 3', Q)
        self.assertIn('clientNonce = cnonce', Q)
        self.assertNotIn('val ha2 = md5("DESCRIBE:$digestUri")', Q)
    def test_challenge_same_socket_before_reconnect(self):
        self.assertIn('send(out, uri, cseq, auth)', Q)
        self.assertIn('if (desc == null)', Q)
        self.assertIn('socket.close()', Q)
    def test_player_sets_core_options_without_changing_uri(self):
        self.assertIn('coreOptions.add("--rtsp-user=$user")', P)
        self.assertIn('coreOptions.add("--rtsp-pwd=$pass")', P)
        self.assertIn('parsedRtsp.encodedAuthority.orEmpty().substringAfterLast', P)
    def test_socket_tests_validate_digest_response(self):
        self.assertIn('authOk(setup, "SETUP"', T)
        self.assertIn('authOk(play, "PLAY"', T)
        self.assertIn('for (reconnect in listOf(false, true))', T)
if __name__ == '__main__': unittest.main()

"""Regressão estrutural: crash assíncrono de CSeq em Media3 1.3.1.
A correção remove o caminho fatal do fluxo normal; não testa câmera física.
"""
from pathlib import Path
import re
import unittest
ROOT=Path(__file__).resolve().parents[1]
PLAYER=(ROOT/'app/src/main/java/com/minhacamera/PlayerActivity.kt').read_text()

class RtspEnginePolicyTests(unittest.TestCase):
    def test_player_starts_only_libvlc(self):
        self.assertIn('private val media3RtspEnabled = false', PLAYER)
        self.assertIn('private var engine = Engine.VLC', PLAYER)
        self.assertIn('if (!active) { active = true; resetAndPlay(Engine.VLC) }', PLAYER)
        self.assertIn('R.id.btnRetry).setOnClickListener { resetAndPlay(Engine.VLC) }', PLAYER)
        self.assertNotIn('resetAndPlay(Engine.MEDIA3)', PLAYER)
        self.assertNotIn('menu.add(0, 2001', PLAYER)
    def test_indirect_media3_is_blocked(self):
        self.assertIn('engine = if (media3RtspEnabled) startEngine else Engine.VLC', PLAYER)
        self.assertIn('if (!media3RtspEnabled) engine = Engine.VLC', PLAYER)
        self.assertIn('engine == Engine.MEDIA3 && media3RtspEnabled', PLAYER)
    def test_other_functionality_preserved(self):
        for name in ['enterPip()', 'beginRecording()', 'startVlc(url, current)', 'index + 1 < urls.size', 'R.id.btnRecordings', 'stopRecording()']:
            self.assertIn(name, PLAYER)
        manifest=(ROOT/'app/src/main/AndroidManifest.xml').read_text()
        self.assertEqual(manifest.count('android:supportsPictureInPicture="true"'), 2)
    def test_release_version(self):
        gradle=(ROOT/'app/build.gradle.kts').read_text()
        self.assertRegex(gradle, r'versionCode\s*=\s*\d+')
        self.assertRegex(gradle, r'versionName\s*=\s*\"1\.4\.\d+\"')

if __name__=='__main__': unittest.main()

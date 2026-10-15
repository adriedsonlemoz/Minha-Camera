"""Regression guard for Build APK 34 protocolSources list."""
from pathlib import Path
import subprocess
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]

class ProtocolSourcesBuild34(unittest.TestCase):
    def test_sources_lists_are_consistent(self):
        result = subprocess.run([sys.executable, str(ROOT / 'scripts/check_protocol_sources.py')],
                                capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_h264_annexb_compiled_by_gradle(self):
        build = (ROOT / 'build.gradle.kts').read_text(encoding='utf-8')
        script = (ROOT / 'scripts/test_protocol.sh').read_text(encoding='utf-8')
        self.assertIn('"PhoneAvcAnnexB"', build)
        self.assertIn('"$ROOT/app/src/main/java/com/minhacamera/PhoneAvcAnnexB.kt"', script)
        self.assertIn('checkAvcAnnexB()', (ROOT / 'tests/ProtocolSmoke.kt').read_text(encoding='utf-8'))
        self.assertIn('python3 scripts/check_protocol_sources.py',
                      (ROOT / '.github/workflows/build-apk.yml').read_text(encoding='utf-8'))

if __name__ == '__main__':
    unittest.main()

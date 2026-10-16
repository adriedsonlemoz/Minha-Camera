"""Regressão do Build APK 45: relatório RTSP com quebras de linha inválidas."""
import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from check_kotlin_strings import invalid_multiline_strings, check


class KotlinStringPreflightTests(unittest.TestCase):
    def test_rejects_build45_literal_line_breaks(self):
        broken = '''val report = details + "
" + firstDebug
if (warn.isNotEmpty()) append("

").append(warn)
.setMessage(getString(msg) + "

" + report)'''
        self.assertGreaterEqual(len(invalid_multiline_strings(broken)), 3)

    def test_accepts_escaped_newlines(self):
        valid = r'''val report = details + "\n" + firstDebug
if (warn.isNotEmpty()) append("\n\n").append(warn)
.setMessage(getString(msg) + "\n\n" + report)'''
        self.assertEqual(invalid_multiline_strings(valid), [])

    def test_accepts_triple_strings_and_kotlin_comments(self):
        valid = '''// "a quote in comment
/* "hello
    /* nested */
" */
val raw = """A
B
C"""
val value = "${if (true) {
    "sim"
} else {
    "não"
}}"
'''
        self.assertEqual(invalid_multiline_strings(valid), [])

    def test_project_passes(self):
        count, errors = check(Path(__file__).resolve().parents[1])
        self.assertGreater(count, 30)
        self.assertEqual(errors, [])


if __name__ == '__main__':
    unittest.main()

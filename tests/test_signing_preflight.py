"""Testes de entrada de Secrets sem chaves privadas nem credenciais reais."""
import base64
import importlib.util
from pathlib import Path
import unittest

module_path = Path(__file__).resolve().parents[1] / 'scripts' / 'prepare_signing.py'
spec = importlib.util.spec_from_file_location('prepare_signing', module_path)
signing = importlib.util.module_from_spec(spec)
spec.loader.exec_module(signing)


class Base64SigningPreflightTest(unittest.TestCase):
    def setUp(self):
        self.fake_keystore = b'\x30\x82' + bytes(i % 251 for i in range(1000))
        self.value = base64.b64encode(self.fake_keystore).decode('ascii')

    def test_canonical(self):
        self.assertEqual(self.fake_keystore, signing.decode_keystore(self.value))

    def test_whitespace_and_outer_quotes(self):
        wrapped = f'"  {self.value[:48]}\r\n{self.value[48:]}  "'
        self.assertEqual(self.fake_keystore, signing.decode_keystore(wrapped))

    def test_missing_padding_from_importer(self):
        self.assertEqual(self.fake_keystore, signing.decode_keystore(self.value.rstrip('=')))

    def test_corrupted_characters_fail(self):
        with self.assertRaisesRegex(ValueError, 'caracteres Base64'):
            signing.decode_keystore(self.value[:100] + '#' + self.value[101:])

    def test_missing_characters_fail(self):
        with self.assertRaises(ValueError):
            signing.decode_keystore(self.value[:-4] + 'A')

    def test_full_env_line_fails(self):
        with self.assertRaisesRegex(ValueError, 'NOME=valor'):
            signing.decode_keystore('KEYSTORE_BASE64=' + self.value)

    def test_trailing_padding_bad(self):
        with self.assertRaises(ValueError):
            signing.decode_keystore(self.value + '==')

    def test_empty_or_undersized_fails(self):
        for value in ('', base64.b64encode(b'not a keystore').decode()):
            with self.subTest(value_len=len(value)), self.assertRaises(ValueError):
                signing.decode_keystore(value)


if __name__ == '__main__':
    unittest.main()

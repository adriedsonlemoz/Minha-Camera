#!/usr/bin/env python3
"""Prepara assinatura fixa do Minha Câmera no runner sem revelar Secrets.

Aceita KEYSTORE_HEX (prioritário, seguro para importação por texto) ou Base64
legado; rejeita entrada corrompida e qualquer certificado diferente do fixado.
"""
from __future__ import annotations

import base64
import binascii
import hashlib
import hmac
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

SECRETS = ("KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
ERROR_HINT = (
    "Importe o novo KEYSTORE_HEX junto de KEYSTORE_PASSWORD, KEY_ALIAS e "
    "KEY_PASSWORD pelo GitHub Manager. Não gere outra chave."
)


def decode_keystore(value: str) -> bytes:
    """Decodifica apenas Base64 padrão, sem adivinhar bytes ausentes."""
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in ("'", '"'):
        value = value[1:-1]
    value = re.sub(r"[\t\n\r\f\v ]", "", value)
    if value.startswith("KEYSTORE_BASE64=") or value.startswith("exportKEYSTORE_BASE64="):
        raise ValueError("O Secret contém a linha NOME=valor, em vez de apenas seu valor.")
    if not value or not re.fullmatch(r"[A-Za-z0-9+/]*={0,2}", value):
        raise ValueError("O Secret não contém apenas caracteres Base64 válidos.")
    body = value.rstrip('=')
    if len(body) % 4 == 1 or (len(value) != len(body) and len(value) - len(body) != ((-len(body)) % 4)):
        raise ValueError("O Base64 possui comprimento ou padding inconsistente.")
    canonical = body + '=' * ((-len(body)) % 4)
    try:
        raw = base64.b64decode(canonical, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise ValueError("Não foi possível decodificar o Base64.") from exc
    # Limites conservadores para keystore binária; não aceitar campos vazios ou enormes.
    if not 512 <= len(raw) <= 512 * 1024:
        raise ValueError("A keystore decodificada possui tamanho inesperado.")
    return raw


def decode_keystore_hex(value: str) -> bytes:
    """Converte somente hexadecimal ASCII. Nenhum caractere é corrigido/adivinhado."""
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in ("'", '"'):
        value = value[1:-1]
    value = re.sub(r"[\t\n\r\f\v ]", "", value)
    if value.startswith("KEYSTORE_HEX="):
        raise ValueError("KEYSTORE_HEX contém a linha NOME=valor, em vez de apenas seu valor.")
    if not value or len(value) % 2 or not re.fullmatch(r"[a-fA-F0-9]+", value):
        raise ValueError("KEYSTORE_HEX não contém hexadecimal válido ou tem tamanho ímpar.")
    raw = bytes.fromhex(value)
    if not 512 <= len(raw) <= 512 * 1024:
        raise ValueError("A keystore HEX decodificada possui tamanho inesperado.")
    return raw


def load_keystore_from_environment() -> tuple[bytes, str]:
    """Hex tem precedência: Base64 legado pode permanecer corrompido no GitHub."""
    hexdigest = os.environ.get('KEYSTORE_HEX', '')
    if hexdigest.strip():
        return decode_keystore_hex(hexdigest), 'HEX'
    legacy = os.environ.get('KEYSTORE_BASE64', '')
    if legacy.strip():
        return decode_keystore(legacy), 'Base64'
    raise ValueError("Faltam KEYSTORE_HEX (recomendado) e KEYSTORE_BASE64 (legado).")


def keytool_certificate(keystore: Path, alias: str) -> bytes:
    """Exporta certificado DER. Senha é passada pelo ambiente, não na linha de comando."""
    cmd = [
        "keytool", "-exportcert", "-storetype", "PKCS12",
        "-keystore", str(keystore), "-storepass:env", "KEYSTORE_PASSWORD",
        "-alias", alias,
    ]
    result = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    if result.returncode != 0 or not result.stdout:
        raise ValueError("Falhou a abertura PKCS12; confira senha e alias dos Secrets.")
    return result.stdout


def prepare() -> None:
    missing = [name for name in SECRETS if not os.environ.get(name)]
    if missing:
        raise ValueError("Secrets obrigatórios ausentes: " + ", ".join(missing))
    runner_temp = os.environ.get("RUNNER_TEMP")
    if not runner_temp:
        raise ValueError("RUNNER_TEMP não está configurado.")

    raw, codec = load_keystore_from_environment()
    expected = (Path(__file__).resolve().parents[1] / 'docs/SIGNING_CERT_SHA256.txt').read_text().strip().lower()
    if not re.fullmatch(r'[a-f0-9]{64}', expected):
        raise ValueError("Impressão SHA-256 fixada no projeto é inválida.")

    dest = Path(runner_temp) / 'minha-camera-release.p12'
    # Não substituir uma chave existente antes de validar a nova.
    with tempfile.NamedTemporaryFile(prefix='.minha-camera-', suffix='.p12', dir=runner_temp, delete=False) as tmp:
        tmp_path = Path(tmp.name)
        tmp.write(raw)
    try:
        tmp_path.chmod(0o600)
        der = keytool_certificate(tmp_path, os.environ['KEY_ALIAS'])
        actual = hashlib.sha256(der).hexdigest()
        if not hmac.compare_digest(actual, expected):
            raise ValueError("Certificado diferente do SHA-256 fixado no projeto; geração bloqueada.")
        tmp_path.replace(dest)
        dest.chmod(0o600)
        print(f"Assinatura verificada: {codec}, PKCS12 e SHA-256 do certificado conferidos.")
    finally:
        tmp_path.unlink(missing_ok=True)


if __name__ == '__main__':
    try:
        prepare()
    except (ValueError, OSError) as error:
        # As mensagens não incluem o valor ou a senha de nenhum Secret.
        print(f"::error::Assinatura Release: {error} {ERROR_HINT}", file=sys.stderr)
        sys.exit(1)

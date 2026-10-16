#!/usr/bin/env python3
"""Detecta texto Android inválido (especialmente apóstrofos crus que AAPT2 rejeita).

XML sintaticamente válido NÃO garante compatibilidade com a compilação de recursos.
Este preflight verifica strings e atributos MaterialButton incompatíveis antes
do Gradle; AAPT2 continua a validação definitiva durante o build.
"""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ALLOWED_ESCAPES = set("\\'\"ntr@?u")
APP_NS = "{http://schemas.android.com/apk/res-auto}"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
INVALID_MATERIAL_INSETS = frozenset(("insetLeft", "insetRight", "insetTop", "insetBottom"))


def errors_in_android_string(text: str):
    errors = []
    i = 0
    while i < len(text):
        ch = text[i]
        if ch == "'":
            errors.append("apóstrofo sem escape: use \\'")
        elif ch == "\\":
            if i + 1 >= len(text):
                errors.append("barra invertida final sem escape")
            else:
                nxt = text[i + 1]
                if nxt not in ALLOWED_ESCAPES:
                    errors.append(f"escape inválido: \\{nxt}")
                if nxt == "u":
                    hexpart = text[i+2:i+6]
                    if len(hexpart) != 4 or any(c not in '0123456789abcdefABCDEF' for c in hexpart):
                        errors.append("escape unicode inválido")
                    i += 4
                i += 1
        i += 1
    return errors


def check(root: Path):
    failures = []
    count = 0
    for path in (root / 'app/src/main/res').rglob('values*/strings.xml'):
        try:
            resources = ET.parse(path).getroot()
        except ET.ParseError as exc:
            failures.append(f'{path}: XML inválido: {exc}')
            continue
        for item in resources:
            if item.tag != 'string':
                continue
            count += 1
            value = ''.join(item.itertext())
            for error in errors_in_android_string(value):
                failures.append(f'{path.name}:{item.attrib.get("name", "?")}: {error}')
    # Build APK 42: app:inset* passa pelo parser XML, mas não existe como
    # atributo no namespace da aplicação e falha no linking do AAPT2.
    for path in (root / 'app/src/main/res').glob('layout*/*.xml'):
        try:
            layout = ET.parse(path).getroot()
        except ET.ParseError as exc:
            failures.append(f'{path}: XML de layout inválido: {exc}')
            continue
        for widget in layout.iter():
            if not widget.tag.endswith('MaterialButton'):
                continue
            wid = widget.attrib.get(ANDROID_NS + 'id', '(sem id)')
            for inset in INVALID_MATERIAL_INSETS:
                if APP_NS + inset in widget.attrib:
                    failures.append(
                        f'{path.name}:{wid}: app:{inset} não é atributo MaterialButton válido; '
                        'remova-o (erro AAPT2 do Build APK 42).'
                    )
    return count, failures


if __name__ == '__main__':
    project = Path(__file__).resolve().parents[1]
    count, failures = check(project)
    if failures:
        print('Falha na validação de recursos Android:\n' + '\n'.join(failures), file=sys.stderr)
        sys.exit(1)
    print(f'Recursos Android: {count} strings verificadas, sem escapes/apóstrofos inválidos.')

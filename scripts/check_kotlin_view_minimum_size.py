#!/usr/bin/env python3
"""Bloqueia minHeight/minWidth (não existentes) em contêineres Android Kotlin.

Button/TextView possuem minHeight/minWidth; para ViewGroup usar setMinimumHeight / setMinimumWidth.
A checagem é dirigida apenas ao escopo direto de *.apply { ... } de contêineres.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
START = re.compile(r"\b(?:LinearLayout|FrameLayout|RelativeLayout)\s*\([^()]*\)\s*\.apply\s*\{")
INVALID = re.compile(r"^\s*(minHeight|minWidth)\s*=")


def find_invalid(source: str):
    errors = []
    for match in START.finditer(source):
        depth = 1
        start = match.end()
        pos = start
        line_start = start
        while pos < len(source) and depth:
            c = source[pos]
            if c == '{':
                depth += 1
            elif c == '}':
                depth -= 1
            if c == '\n' or depth == 0:
                line = source[line_start:pos]
                if depth == 1:
                    bad = INVALID.match(line)
                    if bad:
                        errors.append((source.count('\n', 0, line_start)+1, bad.group(1)))
                line_start = pos+1
            pos += 1
    return errors


def main():
    found = []
    for path in (ROOT/'app/src/main/java').rglob('*.kt'):
        for line, key in find_invalid(path.read_text(encoding='utf-8')):
            found.append(f'{path.relative_to(ROOT)}:{line}: {key} em ViewGroup: use setMinimumHeight/setMinimumWidth')
    if found:
        print('\n'.join(found), file=sys.stderr)
        return 1
    print('Tamanho mínimo de contêineres Kotlin validado.')
    return 0

if __name__ == '__main__':
    raise SystemExit(main())

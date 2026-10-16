#!/usr/bin/env python3
"""Preflight para strings Kotlin com quebras de linha inválidas.

Build APK 45: o parser Kotlin rejeitou aspas duplas envolvendo quebras de
linha reais em MainActivity.kt (relatório RTSP). Strings regulares devem
conter escapes como \\n; strings brutas multiline usam três aspas.

Este scanner léxico acompanha comentários, caracteres, blocos de template
${...}, strings com interpolação e strings triplas. Não substitui o
compilador: detecta apenas o padrão que causou a regressão.
"""
from __future__ import annotations
from dataclasses import dataclass
from pathlib import Path
import sys

@dataclass
class Scope:
    kind: str
    start_line: int = 1
    depth: int = 0


def invalid_multiline_strings(source: str) -> list[str]:
    errors: list[str] = []
    stack: list[Scope] = [Scope('code')]
    i = 0
    line = 1
    n = len(source)
    while i < n:
        s = stack[-1]
        c = source[i]
        next_c = source[i+1] if i + 1 < n else ''
        if s.kind == 'line_comment':
            if c == '\n':
                stack.pop()
            else:
                i += 1
                continue
        elif s.kind == 'block_comment':
            if c == '/' and next_c == '*':
                s.depth += 1
                i += 2
                continue
            if c == '*' and next_c == '/':
                s.depth -= 1
                i += 2
                if s.depth == 0:
                    stack.pop()
                continue
        elif s.kind == 'triple':
            if source.startswith('"""', i):
                stack.pop()
                i += 3
                continue
        elif s.kind == 'string':
            if c == '\\':
                i += 2
                continue
            if c == '"':
                stack.pop()
                i += 1
                continue
            if c == '$' and next_c == '{':
                stack.append(Scope('template', line, 1))
                i += 2
                continue
            if c == '\n':
                errors.append(f'linha {s.start_line}: string regular Kotlin atravessa uma quebra de linha; use \\n ou """..."""')
                stack.pop()
        elif s.kind == 'char':
            if c == '\\':
                i += 2
                continue
            if c == "'":
                stack.pop()
                i += 1
                continue
        else:  # Kotlin code or code within ${...}
            if c == '/' and next_c == '/':
                stack.append(Scope('line_comment'))
                i += 2
                continue
            if c == '/' and next_c == '*':
                stack.append(Scope('block_comment', depth=1))
                i += 2
                continue
            if source.startswith('"""', i):
                stack.append(Scope('triple', line))
                i += 3
                continue
            if c == '"':
                stack.append(Scope('string', line))
                i += 1
                continue
            if c == "'":
                stack.append(Scope('char', line))
                i += 1
                continue
            if s.kind == 'template':
                if c == '{':
                    s.depth += 1
                elif c == '}':
                    s.depth -= 1
                    if s.depth == 0:
                        stack.pop()
        if c == '\n':
            line += 1
        i += 1
    return errors


def check(root: Path) -> tuple[int, list[str]]:
    files = sorted((root / 'app/src').rglob('*.kt')) + sorted((root / 'tests').rglob('*.kt'))
    errors: list[str] = []
    for path in files:
        for problem in invalid_multiline_strings(path.read_text(encoding='utf-8')):
            errors.append(f'{path.relative_to(root)}: {problem}')
    return len(files), errors


if __name__ == '__main__':
    num, issues = check(Path(__file__).resolve().parents[1])
    if issues:
        print('Strings Kotlin inválidas:\n' + '\n'.join(issues), file=sys.stderr)
        sys.exit(1)
    print(f'Strings Kotlin: {num} arquivos verificados; sem quebras de linha inválidas.')

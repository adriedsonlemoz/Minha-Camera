#!/usr/bin/env python3
"""Evita literais de cor ARGB tratados como Long em chamadas Kotlin que exigem Int.

O bit 31 de uma cor ARGB geralmente fica definido. Literais hexadecimais Kotlin
> Int.MAX_VALUE são inferidos como Long e precisam de conversão explícita.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
COLOR_CALL = re.compile(
    r"\b(setBackgroundColor|setTextColor|setHintTextColor|setLinkTextColor|setStrokeColor)"
    r"\s*\(\s*(0[xX][0-9a-fA-F]{8})\s*\)"
)


def find_unsafe_argb(source: str):
    for match in COLOR_CALL.finditer(source):
        if int(match.group(2), 16) > 0x7FFFFFFF:
            line = source.count("\n", 0, match.start()) + 1
            yield line, match.group(0)


def main() -> int:
    directory = ROOT / "app/src/main/java"
    problems = []
    for path in directory.rglob("*.kt"):
        for line, expression in find_unsafe_argb(path.read_text(encoding="utf-8")):
            problems.append(f"{path.relative_to(ROOT)}:{line}: {expression}: converter cor para Int com .toInt()")
    if problems:
        print("ARGB Kotlin inválido:\n" + "\n".join(problems), file=sys.stderr)
        return 1
    print("ARGB Kotlin: nenhum literal de cor Long passado diretamente a métodos Int.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Preflight Kotlin: prevent duplicate and ambiguous imports before Gradle compilation.

Build APK 35 failed because PhoneHeadlessPreview.kt imported SystemClock twice.
Build APK 37 failed because PhoneCameraViewerActivity.kt used Intent without importing it.
"""
from __future__ import annotations
from pathlib import Path
import re
import sys

IMPORT_RE = re.compile(r"^\s*import\s+([A-Za-z_][\w.]*(?:\.\*)?)(?:\s+as\s+(\w+))?\s*$")


def import_problems(source: str) -> list[str]:
    seen_paths: dict[str, int] = {}
    seen_names: dict[str, tuple[str, int]] = {}
    problems: list[str] = []
    for lineno, line in enumerate(source.splitlines(), 1):
        match = IMPORT_RE.match(line)
        if not match:
            continue
        path, alias = match.groups()
        key = f"{path} as {alias}" if alias else path
        if key in seen_paths:
            problems.append(f"linha {lineno}: import repetido de {key} (linha {seen_paths[key]})")
        else:
            seen_paths[key] = lineno
        # Imports wildcard não definem um nome simples local e não entram na checagem de colisão.
        if path.endswith(".*"):
            continue
        name = alias or path.rsplit(".", 1)[-1]
        previous = seen_names.get(name)
        if previous is not None and previous[0] != path:
            problems.append(f"linha {lineno}: nome {name} ambíguo entre {previous[0]} "
                            f"(linha {previous[1]}) e {path}")
        elif previous is None:
            seen_names[name] = (path, lineno)
    # Classes from the Android SDK are not implicitly imported in Kotlin.
    # Guard a recurring regression in viewer Activities where an unqualified
    # Intent constructor is added without its android.content.Intent import.
    if re.search(r"(?<![\w.])Intent\s*\(", source):
        imports_intent = bool(re.search(r"^\s*import\s+android\.content\.(?:Intent|\*)\s*$", source, re.M))
        local_intent = bool(re.search(r"\b(?:class|object|fun)\s+Intent\b", source))
        if not imports_intent and not local_intent:
            problems.append("uso de Intent(...) sem import android.content.Intent")
    return problems


def main(root: Path) -> int:
    files = sorted((root / "app/src").rglob("*.kt")) + sorted((root / "tests").rglob("*.kt"))
    if not files:
        print("ERRO: nenhum arquivo Kotlin encontrado", file=sys.stderr)
        return 1
    findings = [(p, problem) for p in files
                for problem in import_problems(p.read_text(encoding="utf-8"))]
    for path, problem in findings:
        print(f"ERRO: {path.relative_to(root)}: {problem}", file=sys.stderr)
    if findings:
        return 1
    print(f"Imports Kotlin verificados: {len(files)} arquivos sem duplicação/ambiguidade")
    return 0


if __name__ == "__main__":
    sys.exit(main(Path(__file__).resolve().parents[1]))

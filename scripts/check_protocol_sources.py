#!/usr/bin/env python3
"""Ensure Gradle and shell protocol tests compile the same pure Kotlin source files.

GitHub Actions Build APK 34 failed because PhoneAvcAnnexBSmoke.kt referenced
PhoneAvcAnnexB, which was present in test_protocol.sh but absent in the
root build.gradle.kts protocolSources list.
"""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[1]
build = (root / "build.gradle.kts").read_text(encoding="utf-8")
shell = (root / "scripts/test_protocol.sh").read_text(encoding="utf-8")
source_list = re.search(r"listOf\((.*?)\)\.map\s*\{", build, flags=re.S)
if source_list is None:
    sys.exit("ERRO: lista protocolSources não encontrada no Gradle")
gradle_sources = re.findall(r'"([A-Za-z][A-Za-z0-9_]*)"', source_list.group(1))
shell_sources = re.findall(r"/app/src/main/java/com/minhacamera/([A-Za-z][A-Za-z0-9_]*)\.kt", shell)
if not gradle_sources or not shell_sources:
    sys.exit("ERRO: fontes Kotlin não identificadas em Gradle ou script shell")
missing_in_gradle = sorted(set(shell_sources) - set(gradle_sources))
missing_in_shell = sorted(set(gradle_sources) - set(shell_sources))
duplicates_gradle = sorted(s for s in set(gradle_sources) if gradle_sources.count(s) > 1)
duplicates_shell = sorted(s for s in set(shell_sources) if shell_sources.count(s) > 1)
missing_files = sorted(s for s in set(gradle_sources) | set(shell_sources)
                       if not (root / f"app/src/main/java/com/minhacamera/{s}.kt").is_file())
if missing_in_gradle or missing_in_shell or duplicates_gradle or duplicates_shell or missing_files:
    print("ERRO: fontes dos testes de protocolo inconsistentes", file=sys.stderr)
    for name, value in (
        ("ausentes no Gradle", missing_in_gradle),
        ("ausentes no shell", missing_in_shell),
        ("duplicadas no Gradle", duplicates_gradle),
        ("duplicadas no shell", duplicates_shell),
        ("arquivos ausentes", missing_files),
    ):
        if value:
            print(f" - {name}: {', '.join(value)}", file=sys.stderr)
    sys.exit(1)
print(f"Fontes de protocolo sincronizadas: {len(gradle_sources)} arquivos Kotlin no Gradle e no shell")

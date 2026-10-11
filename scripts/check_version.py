#!/usr/bin/env python3
"""Validação simples de metadados de release, usada antes de gerar APK."""
from pathlib import Path
import re
import sys
root = Path(__file__).resolve().parents[1]
gradle = (root / 'app/build.gradle.kts').read_text()
name = re.search(r'versionName\s*=\s*"([^"]+)"', gradle).group(1)
code = int(re.search(r'versionCode\s*=\s*(\d+)', gradle).group(1))
files = {
    'AGENTS.md': f'Versão atual: {name}+{code}',
    'README.md': f'Versão atual: {name} (versionCode {code})',
    'CHANGELOG.md': f'## {name}+{code}',
    'docs/VERSIONING.md': f'**{name}+{code}**',
    'docs/RELEASE_NOTES.md': f'Minha Câmera {name} (versionCode {code})',
    'app/src/main/res/values/strings.xml': f'Versão {name} (código {code})',
}
failed = []
for filename, marker in files.items():
    if marker not in (root / filename).read_text():
        failed.append(f'{filename}: ausente {marker!r}')
if failed:
    print('Metadados inconsistentes:\n' + '\n'.join(failed), file=sys.stderr)
    sys.exit(1)
print(f'Versionamento sincronizado: {name}+{code}')

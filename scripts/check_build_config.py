#!/usr/bin/env python3
"""Evita incompatibilidade de SDK/AGP (build #7) e ausência de sdkmanager (build #8)."""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[1]
root_gradle = (root / "build.gradle.kts").read_text()
app_gradle = (root / "app/build.gradle.kts").read_text()
actions = (root / ".github/workflows/build-apk.yml").read_text()

def extract(pattern: str, data: str, description: str) -> str:
    m = re.search(pattern, data)
    if not m:
        raise ValueError(f"Não foi encontrada a configuração de {description}")
    return m.group(1)

try:
    sdk = int(extract(r"compileSdk\s*=\s*(\d+)", app_gradle, "compileSdk"))
    target = int(extract(r"targetSdk\s*=\s*(\d+)", app_gradle, "targetSdk"))
    agp = extract(r'id\("com\.android\.application"\) version "([0-9.]+)"', root_gradle, "Android Gradle Plugin")
    kotlin = extract(r'id\("org\.jetbrains\.kotlin\.android"\) version "([0-9.]+)"', root_gradle, "Kotlin Gradle Plugin")
    gradle = extract(r"gradle-version:\s*'([0-9.]+)'", actions, "Gradle GitHub Actions")
    errors = []
    if 'org.videolan.android:libvlc-all:3.7.7' in app_gradle and sdk < 36:
        errors.append('LibVLC 3.7.7 requer compileSdk >= 36.')
    if (agp, gradle, kotlin) != ('8.11.1', '8.13', '2.2.20'):
        errors.append('Conjunto validado neste projeto: AGP 8.11.1 / Gradle 8.13 / Kotlin 2.2.20. Revisar a matriz de compatibilidade antes de atualizar.')
    if '"platforms;android-36"' not in actions:
        errors.append('GitHub Actions precisa instalar a plataforma android-36.')
    setup_sdk = actions.find('uses: android-actions/setup-android@v4')
    install_sdk = actions.find('sdkmanager --install "platforms;android-36"')
    if setup_sdk == -1 or install_sdk <= setup_sdk:
        errors.append('Preparar Android SDK com setup-android@v4 ANTES de executar sdkmanager.')
    if 'command -v sdkmanager' not in actions:
        errors.append('Verificar a disponibilidade de sdkmanager antes da instalação.')
    if 'yes | sdkmanager --licenses' in actions:
        errors.append('Não ignorar falhas de sdkmanager na aceitação de licenças.')
    if target != 34:
        errors.append('Esta correção deve preservar targetSdk 34 até revisão de compatibilidade em runtime.')
    if errors:
        raise ValueError('\n'.join(errors))
    print(f'Configuração Android consistente: compileSdk {sdk}, targetSdk {target}, AGP {agp}, Gradle {gradle}, Kotlin {kotlin}')
except ValueError as exc:
    print(str(exc), file=sys.stderr)
    sys.exit(1)

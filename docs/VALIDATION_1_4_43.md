# Validação da versão 1.4.43+53

Data: 2026-10-16

## Alterações validadas
- Tela **Usar o celular como câmera** reorganizada para exibir controles/ajustes em cards visíveis na própria tela.
- Inclusão da ação **Analisar e aplicar** para câmeras RTSP já cadastradas, disponível no menu de opções por toque longo no cartão.
- Sincronização de `versionName`/`versionCode`, README, CHANGELOG, VERSIONING, RELEASE_NOTES e histórico de novidades.

## Verificações executadas
- `python3 scripts/check_version.py` → **OK** (`1.4.43+53` sincronizado).
- `python3 scripts/check_build_config.py` → **OK** (compileSdk 36, targetSdk 34, AGP 8.11.1, Gradle 8.13, Kotlin 2.2.20).
- `python3 scripts/check_android_resources.py` → **OK** (360 strings verificadas, sem escapes/apóstrofos inválidos).
- `python3 scripts/check_kotlin_imports.py` → **OK** (55 arquivos Kotlin sem duplicação/ambiguidade de imports).

## Pendências
- Não foi possível executar build Gradle/Android local porque o pacote entregue não inclui `gradlew`.
- APK/CI e teste em aparelho continuam pendentes, principalmente para confirmar o layout do transmissor e o fluxo da ação RTSP **Analisar e aplicar**.

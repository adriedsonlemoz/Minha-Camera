# Validação 1.4.56+66 — 2026-10-16

## Origem da falha
- Logs `Minha-Camera-Build-APK-55-logs.zip`, etapa "Testar protocolos e compilar todos os APKs release": `:app:compileReleaseKotlin` falhou em `PhoneCameraViewerActivity.kt:255` (`viewerFullPip` inexistente) e `:345` (`landscapeDismissedByUser` inexistente).
- As etapas anteriores do GitHub Actions completaram, incluindo assinatura prévia e processamento de recursos; a tarefa de compilação Kotlin não completou e nenhum APK foi produzido nesse build.

## Correção aplicada
- Listener de `viewerFullPip` excluído pois não há esse widget na camada de tela cheia; o PiP da grade comum (`viewerPipAction`) foi preservado.
- Estado obsoleto trocado por `dismissedFullscreenOrientation = null` ao sair de PiP, coerente com `syncLandscapeFullscreen()`.
- Check de correspondência `R.id` ↔ IDs declarados em XML do receptor e testes de regressão, integrados ao preflight do Actions.
- Versionamento e documentação sincronizados em `1.4.56+66`, sem mudanças em certificados, keystore ou dependências.

## Verificações executadas
- `python3 -m unittest discover -s tests -p 'test_*.py' -q`: **115 testes aprovados**, incluindo os 3 novos de regressão do Build APK 55.
- `scripts/check_version.py`, `check_android_resources.py`, `check_viewer_bindings.py`, `check_build_config.py`, `check_protocol_sources.py`, `check_kotlin_imports.py` e `check_kotlin_strings.py`: aprovados.
- 72 XML parseados corretamente; 371 textos Android verificados; 66 Kotlin inspecionados para imports e strings.
- `scripts/test_protocol.sh`: os primeiros quatro grupos Kotlin/JVM concluíram com sucesso; o comando inteiro atingiu limite de tempo no último grupo. Esse último grupo (`RtspSessionSmoke`) foi então compilado/executado isoladamente e aprovou **4 cenários RTSP Digest DESCRIBE/SETUP/PLAY**. Portanto todos os grupos passaram, mas a execução integral sequencial da suíte não chegou ao fim dentro do limite.

## Limitações
- Não foi compilado um APK completo com o Gradle/Android SDK nesta sessão. Ainda é necessário confirmar `:app:compileReleaseKotlin`, `assembleRelease` e assinatura no GitHub Actions com os Secrets já configurados.
- Não foi realizado teste de câmera física, MJPEG/H.264 ou PiP no aparelho Android.

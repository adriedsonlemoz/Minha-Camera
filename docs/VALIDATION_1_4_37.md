# Validação 1.4.37+47 | Build APK 37

- Fonte dos logs: Minha-Camera-Build-APK-37-logs (1).zip, execução 2026-10-15.
- O Gradle passou em `:compileProtocolTests`, `:testProtocol`, recursos e empacotamento JNI, mas falhou em `:app:compileReleaseKotlin` na linha 416 de `PhoneCameraViewerActivity.kt`: `Unresolved reference Intent`.
- Corrigido import `android.content.Intent` e acrescentados testes/preflight para o uso de `Intent(...)` sem import explícito.
- Executar novo `gradle testProtocol testReleaseUnitTest assembleRelease` no GitHub Actions para confirmar APK e certificado. A revisão local não comprova execução no Redmi A3.

## Verificações locais concluídas

- `python -m unittest discover -s tests -p '*.py' -q`: 66 testes aprovados.
- `bash scripts/test_protocol.sh`: concluído, incluindo MCRP v1, autenticação de comandos remotos e media HTTP 206, protocolo H.264, servidor MJPEG e RTSP simulado.
- `scripts/check_version.py`: 1.4.37+47 sincronizado.
- `scripts/check_kotlin_imports.py`: 49 arquivos Kotlin sem imports duplicados/ambíguos ou uso não qualificado de Intent sem import.
- `scripts/check_android_resources.py`: 342 strings validadas; `check_build_config.py` e `check_protocol_sources.py`: aprovados.
- Nenhum build completo Android, assinatura ou teste físico realizado neste ambiente.

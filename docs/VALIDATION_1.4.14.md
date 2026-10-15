# Validação 1.4.14+24: redesenho de interface

Escopo: recursos e interface nativa, sem alterações em credenciais, protocolos, keystore ou certificado.

- [x] `python3 scripts/check_version.py`
- [x] `python3 scripts/check_build_config.py`
- [x] `python3 tests/test_ui_1414.py`
- [x] `bash scripts/test_protocol.sh`
- [x] XML/manifest e ZIP válidos
- [ ] `gradle testProtocol testReleaseUnitTest assembleRelease --build-cache` no Actions com Secrets
- [ ] Testes reais no transmissor e receptor, incluindo QR popup/fechar, câmera frontal/traseira, modo retrato, modo noturno, mesmo Wi-Fi e vídeo

Em caso de falha, não assumir que a execução anterior passou. A chave privada segue fora do projeto.

## Resultado nesta rodada

- Kotlin PSI: 22 arquivos de produção sem erros de sintaxe.
- XML: arquivos de manifest, layout, cores, vetores e strings analisados por parser XML.
- Testes estruturais: navegação inferior, listas separadas, modal QR, scanner QR incorporado, prévia TextureView e validação local passaram.
- Testes de protocolo Kotlin/JVM executados: ONVIF, RTSP, SDP H.265, MJPEG/token, gravações/arquivos e máscaras IPv4 aprovados (somente simulados).
- Sem toolchain Android/Gradle completa nesta execução; teste instrumentado e APK Release não realizados.
- A UI é uma implementação baseada nas referências fornecidas, e ainda precisa de revisão visual em um dispositivo real.

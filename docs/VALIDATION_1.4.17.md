# Validação 1.4.17+27

Validação local de **código-fonte**. Não representa build Android nem ensaios em aparelhos.

- PSI Kotlin: arquivos de produção analisados, sem erros sintáticos.
- XML: layout, Manifest, recursos e ícones analisados com parser XML.
- Validação sequencial `scripts/check_version.py` e configurações `scripts/check_build_config.py`.
- Testes estáticos dos controles, destinos Downloads, Camera2, do pareamento QR e da assinatura existente.
- Testes de conversão NV21 para encoder planar/semiplanar e composição empilhada no Kotlin JVM.
- Regressão de protocolos ONVIF/RTSP/MJPEG tentou execução local, mas não concluiu dentro do tempo limite, portanto **não aprovada nem reprovada nesta rodada**.

## Pendências

- GitHub Actions: Gradle `testProtocol testReleaseUnitTest assembleRelease` com assinatura estável.
- Android físico: gravação MP4 utilizável, integração MediaStore/Downloads, permissão em Android 7–9, cancelamento, falta de espaço.
- Android 11+ com suporte concorrente: confirmar câmera frontal/traseira, fluxo sem congelamentos, rotação e degradação por calor.
- Dois celulares: quantificar FPS, perda de quadros, latência, largura de banda e quedas Wi-Fi.

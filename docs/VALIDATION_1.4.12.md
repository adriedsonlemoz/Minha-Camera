# Validação local da 1.4.12+22

Origem: 1.4.11+21, após captura da tela do transmissor mostrando QR Code e a prévia preta. A captura comprova aparência preta, não confirma qual camada causou a falha nem a disponibilidade de quadros codificados.

## Modificações

- Layout troca `SurfaceView` por `TextureView`, prévia primeiro, QR Code em seguida.
- Lifecycle usa `SurfaceTextureListener`, `setPreviewTexture`, gestão da rotação, liberação da câmera ao pausar e ao destruir a textura.
- Status de transmissão apenas após codificação/publicação de JPEG; diagnóstico depois de cinco segundos se não houver vídeo capturado/codificado ou se a textura não receber atualizações.
- Configuração de dependências, assinaturas, codificação MJPEG e RTSP/ONVIF preservados.

## Validação

- `python3 scripts/check_version.py`: aprovado, 1.4.12+22.
- `python3 scripts/check_build_config.py`: aprovado, SDK/Gradle/Kotlin/LibVLC preservados.
- `python3 tests/test_preview_integration.py`: aprovado, prévia TextureView, ordem da interface, gestão do SurfaceTexture, status condicionado ao JPEG e proporção geométrica.
- `python3 tests/test_qr_integration.py`: aprovado, fluxo QR e manifesto.
- Parser de XML: 26 arquivos válidos.
- `bash scripts/test_protocol.sh`: execução tentada, interrompida por limite de tempo do ambiente; **não é resultado aprovado nem reprovação de protocolos**.
- `gradle ... assembleRelease`: indisponível neste ambiente (sem Gradle/Android SDK completos).

Os testes estáticos não equivalem ao teste no dispositivo.

## Limitações

- A imagem fornecida não permite determinar se o problema anterior era a composição SurfaceView ou se o sensor/driver deixou de produzir quadros. A nova versão diferencia esses sintomas mas precisa de teste no celular afetado.
- Não compilado APK neste ambiente caso SDK/Gradle não estejam disponíveis; testes físicos de câmera e rede continuam pendentes.

# Verificação local da versão 1.4.10+20

Data: 2026-10-14. Projeto analisado a partir do arquivo enviado `Minha-Camera-20261014-172448-main.zip`.

## Executado

- `python3 scripts/check_version.py`: aprovado (`1.4.10+20`).
- `python3 scripts/check_build_config.py`: aprovado (compileSdk 36, targetSdk 34, AGP 8.11.1, Gradle 8.13, Kotlin 2.2.20).
- `PATH=/root/.sdkman/candidates/kotlin/1.9.0/bin:$PATH bash scripts/test_protocol.sh`: 10 grupos de testes sintéticos aprovados. Incluem geração/consumo de multipart MJPEG com quadro binário, rejeição HTTP 403 por rota sem token válido, aceitação/rejeição de URL local, rotação NV21, ONVIF, RTSP/SDP/Digest, sub-redes, gravação em arquivos, geometria e identificação de dispositivos.
- Sintaxe XML verificada pelo parser XML Python, incluindo Manifest, layouts e recursos. Sem erros.
- Código novo MJPEG testado no JVM (`PhoneCameraServer`, `PhoneCameraUrls`, `Nv21Frames`).

## Ainda não executado

- Não há Android SDK/AGP/Gradle instalados neste ambiente; não foi possível executar `gradle testProtocol testReleaseUnitTest assembleRelease --build-cache` nem gerar APKs.
- A compilação Android das Activities novas, permissões CAMERA, captura Camera1, troca frontal/traseira, configuração da interface e comportamento de compartilhamento **ainda precisam ser verificados em CI e em dois aparelhos reais**.
- Assinatura Release continua usando a chave Debug configurada, que pode diferir da instalada. Não gerar nova chave como solução de atualização.
- Não há teste de latência, CPU, taxa de quadros, consumo energético, conectividade em hotspot e redes com isolamento de clientes.

## Risco conhecido de projeto

O servidor está associado à Activity de captura, não a um serviço Foreground: ao abrir outro aplicativo ou bloquear a tela, transmite zero quadros. O código de acesso permanece estável até revogação, mas HTTP não é cifrado; usar apenas Wi-Fi confiável. Não há reconexão automática quando o emissor volta; o receptor mostra botão «Tentar novamente». A implementação Camera1 pode não funcionar com todas as variantes Android/OEM. O servidor usa a porta 8765, exigindo que ela não esteja ocupada.

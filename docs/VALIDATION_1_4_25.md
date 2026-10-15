# Validação 1.4.25+35 | PiP e fechamento ao abrir RTSP

## Evidência recebida

O MP4 de 2026-10-15 mostra o usuário tocando **Abrir** na câmera RTSP “Câmera 209”; o app sai para a tela inicial em menos de 1 segundo. Não há log de exceção no vídeo. O problema é na abertura do RTSP, não na conexão MJPEG entre telefones. Nenhuma causa específica pode ser afirmada sem `adb logcat` ou relatório de crash Android.

## Alterações

- PiP Android (API 26+) declarado para os dois visualizadores, usando a API nativa, sem `SYSTEM_ALERT_WINDOW`.
- Receptor move a mesma View da prévia para host de tela cheia PiP, sem duplicar decoder JPEG. Receptor não interrompe no `onPause`, somente no `onStop` fora do PiP e no `onDestroy`.
- Player RTSP mantém Media3/LibVLC ativos quando em PiP e os libera ao sair; PiP somente após primeiro quadro e sem gravação em curso.
- Media3 cobre `LinkageError` na inicialização. Relatório seguro Java/Kotlin, armazenado em preferências privadas, aparecendo na inicialização seguinte (sem exception.message, URLs ou senhas); não cobre crash de camada nativa.

## Limites e reprodução

- Testar com “Câmera 209” e gravar Logcat: `adb logcat -c`, iniciar o app, tocar Abrir, executar `adb logcat -d -v time -b main -b crash > logcat.txt`. **O Logcat pode conter senhas e dados pessoais**: revise antes de compartilhar.
- Se houver registro de falha na próxima inicialização, copie e envie seu texto. Se o Android voltar à tela inicial sem registro, suspeitar de falha nativa ou morte do processo, confirmar com Logcat.
- Repetir com receptor MJPEG e RTSP em PiP, alternando para mapas, espelho, reconexão e encerramento. Em aparelhos sem FEATURE_PIP exibir aviso. Testar permissões PiP do Xiaomi/HyperOS.
- **Não afirma correção do fechamento na câmera do vídeo:** foi adicionado caminho de diagnóstico e proteção contra uma classe de falhas, mas falta exceção real.
- O Redmi A3 transmissor continua exigindo tela/Activity ativa. Sem serviço foreground de câmera e sem promessa de transmissão com tela apagada.

## Testes locais

- `scripts/check_version.py`: OK.
- `scripts/check_build_config.py`: OK (AGP 8.11.1, Kotlin 2.2.20, SDK 36).
- `scripts/check_android_resources.py`: 238 strings, OK.
- `python -m unittest discover -s tests -p test_*.py`: **33 testes OK** (incluindo 5 novos casos PiP/crash).
- `bash scripts/test_protocol.sh`: smoke Kotlin/JVM de protocolos, eco e câmera, **OK** (avisos de API URL deprecated).
- Parser PSI: 30 fontes Kotlin, **0 erros de sintaxe**.
- XML estruturais e ZIP CRC: OK.
- Gradle `assembleRelease`, emulador e dispositivos reais não puderam ser executados neste ambiente (sem SDK/Gradle Android). Não foi gerado APK assinado local.

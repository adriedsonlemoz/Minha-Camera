# Validação da versão 1.4.26+36

## Evidência da falha e análise

Log real enviado em 2026-10-15 às 10:31:02:
`ExoPlayer:Playback → NullPointerException → RtspClient$MessageListener.handleRtspResponse:544`.

No fonte oficial do AndroidX Media3 **1.3.1**, `RtspClient.java:544` executa `checkNotNull(response.headers.get(RtspHeaders.CSEQ))`; portanto, uma resposta sem valor `CSeq` disponível provoca NPE em uma thread `HandlerThread` e encerra o processo, sem chamar `Player.Listener.onPlayerError`. Não presumir sem captura bruta que o firmware da câmera seja culpado: proxy/parser também podem interferir.

Fonte auditável: https://github.com/androidx/media/blob/1.3.1/libraries/exoplayer_rtsp/src/main/java/androidx/media3/exoplayer/rtsp/RtspClient.java#L544

## Mitigação

- Motor inicial RTSP: **LibVLC 3.7.7**. `onStart()`, botão Retry e menu **Reproduzir com LibVLC** apontam para LibVLC.
- Entrada manual Media3 removida do menu. `resetAndPlay()` e `playCurrent()` impõem o bloqueio `media3RtspEnabled=false` em qualquer caminho indireto.
- Mantidos Media3 e ExoPlayer no projeto, mas temporariamente inacessíveis como motor RTSP. Reavaliar após atualizar biblioteca e testar malformações de respostas; não tentar recuperar de uncaught exception via `Thread.UncaughtExceptionHandler`.
- URLs alternativas seguem com LibVLC, com timeout por tentativa e exibição de falhas. Se todos falharem, a UI de erro informa isso sem tentar Media3.
- PiP RTSP e receptor MJPEG mantidos. `record()` ocorre no player LibVLC ativo, sem reconexão Media3 → LibVLC.

## Verificações locais

- `python3 scripts/check_version.py`: aprovado, `1.4.26+36`.
- `python3 scripts/check_android_resources.py`: aprovado, 238 strings.
- `python3 scripts/check_build_config.py`: aprovado.
- `python3 -m unittest discover -s tests -p 'test_*.py' -q`: 37 testes aprovados, incluindo política anti-crash RTSP.
- Kotlin PSI: 30 fontes `.kt` analisadas, sem erros de sintaxe.

**Não executado:** Gradle (`testProtocol`, `testReleaseUnitTest`, `assembleRelease`), Android lint, validação de APK, reprodução na câmera RTSP real, PiP em dispositivo e fluxos H.264/H.265. O ambiente não contém `gradle` e o SDK/keystore Release necessários. O ZIP desta versão contém somente fontes e scripts; não há APK compilado.

## Risco remanescente

LibVLC pode falhar com certas implementações RTSP ou gerar falhas nativas independentes. Testar Câmera 209 com 1.4.26 e relatar erro de UI/logcat caso ocorra. A troca de motor é um contorno confiável para **este caminho de NPE do Media3**, não uma garantia universal de reprodução. Nunca incluir URLs com usuário/senha em relatórios.

# Minha Câmera 1.4.48+58 — validação local (2026-10-16)

## Alterações
- Diagnóstico com botão de bug, janela própria, copiar e exportação TXT por SAF.
- Classificação de serviço RTSP em `RtspServiceDetector` e recuperação limitada à rede local privada.
- Novo URL só salvo após o primeiro quadro real, com confirmação explícita prévia.
- Tela de boas-vindas da versão atual (sem amontoar histórico).

## Testes concluídos
- 83 testes Python aprovados.
- Testes Kotlin/JVM de protocolo, playback TCP/UDP e detecção de serviços concluídos (incluindo 5 cenários de serviço).
- Parser Kotlin PSI: 50 arquivos, nenhum erro sintático.
- Imports Kotlin: 61 fontes sem duplicação.
- 71 XMLs válidos; strings Android e versionamento sincronizados.

## Limites
- Testes unitários/sintaxe e integridade do ZIP não são build APK real.
- O serviço da câmera 209 não está disponível neste ambiente; não foi possível verificar RTSP/ONVIF dela. Porta aberta sem resposta RTSP válida pode ser serviço diferente ou RTSP/NVR desativado.
- Continuam pendentes compileReleaseKotlin, processReleaseResources, assembleRelease, assinatura e teste no aparelho.

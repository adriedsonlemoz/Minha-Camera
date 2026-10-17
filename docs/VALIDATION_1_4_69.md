# Validação 1.4.69+79 — 2026-10-17

- 172 testes Python `unittest discover` aprovados, inclusive testes novos de download, miniaturas, cabeçalho de zoom e saída da tela cheia.
- 62 fontes Kotlin do módulo analisadas por parser PSI (zero erros sintáticos).
- 82 XML de recursos validados; verificadores de IDs Kotlin/Android, strings, imports, versão e recursos passaram.
- Protocolos Kotlin/JVM: os grupos de simulações MCRP, comandos autenticados, RTSP, H.264 Annex-B, orientação NV21, handoff de tela apagada, pinch e FPS publicado registraram sucesso no log local; a execução única ultrapassou o timeout do comando externo e o exit code agregado não foi capturado. Novo teste `PhonePublishedFpsSmoke.kt` também executado isoladamente com sucesso.
- ZIP validado por leitura completa de CRC após geração.
- **Pendente:** Gradle assembleRelease, testes físicos Redmi A3/receiver e experimento com H.264 (imagem verde). A preferência YUV420 planar é hipótese técnica, não solução comprovada.
- API Camera1 pode ocultar lentes ultra-wide; não inventar disponibilidade. Teste de câmera extra deve enumerar IDs expostos em Android real e validar abertura segura antes de oferecer um item.
- Miniaturas de vídeo são extraídas por MediaMetadataRetriever/HTTP Range sob demanda: o extrator pode não suportar certos formatos/servidores, permanecendo o ícone.
- Orientação EXIF de fotos existentes somente quando metadado presente; nova captura recebe Camera.Parameters.setRotation, sem dupla rotação da prévia.

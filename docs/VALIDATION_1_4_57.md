# Validação 1.4.57+67

- Projeto-fonte recebido: Minha-Camera-v1.4.56-Build55-Kotlin-corrigido-source.zip.
- 118 testes Python/JVM auxiliares passaram após atualização dos testes cuja expectativa antiga era tela cheia também no retrato.
- `check_kotlin_strings.py`, `check_viewer_bindings.py`, `check_android_resources.py`: aprovados.
- A câmera real, estabilidade com tela apagada, H.264, SAF export em Android, PiP e rotação dependem de testes físicos.
- Gradle/Android SDK não disponíveis neste ambiente; build Release e testes instrumentados pendentes.
- `Pausar` só para MJPEG, sem fechar servidor; clientes podem exibir último frame até a retomada ou reconectar se excederem o timeout. Pause H.264 não implementada.
- IMPORTANTE: layout de transmissão usa o mesmo TextureView para evitar destruição da Surface durante mudança entre retrato e paisagem.

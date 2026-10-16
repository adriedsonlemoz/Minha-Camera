# Validação 1.4.49+59

- Teste recebido: `Minha-Camera-RTSP-diagnostico.txt` indicou quatro falhas imediatas LibVLC para `192.168.100.209:554` nos caminhos `/onvif1` e `/onvif2` via TCP/UDP, sem vídeo e sem código RTSP ou SDP conclusivos.
- Alterações de interface: centralização vertical da tela de boas-vindas com ScrollView.fillViewport; estilo consistente dos três botões de diagnóstico.
- Diagnóstico: janela aberta recebe o resultado de `DESCRIBE` quando terminar e gera relatório sem duplicar mensagens.
- Build Gradle/Android e testes de interface em dispositivo ainda pendentes.

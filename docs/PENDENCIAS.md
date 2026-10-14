# Pauta técnica após 1.4.11

## P0: preservação de dados / segurança

- Persistir credenciais RTSP com proteção baseada no Android Keystore e migração sem perda das câmeras legadas.
- Garantir assinatura de APK estável; recuperar a assinatura original antes de prometer upgrade direto sem perda.

## P1: robustez

- Adicionar timeout global/cancelamento e lifecycle apropriado para consultas ONVIF/RTSP.
- Recuperar sessões de gravação interrompidas ou descartá-las com confirmação.
- Validar cada octeto de IPv4 e portas entre 1–65535 nos formulários manuais.
- Medir e validar H.264/H.265 e fallback em câmeras reais.

## P2: câmera entre celulares

- Testar transmissão e pareamento QR no mínimo em dois aparelhos distintos, incluindo Wi-Fi sem internet e hotspot.
- Revisar disponibilidade da API Camera1, desempenho, largura de banda, reconexão e consumo energético.
- Não usar HTTP MJPEG em redes inseguras; avaliar TLS, WebRTC ou H.264 para confidencialidade e eficiência.
- Resolver reconexão automática após mudança de IP sem expor códigos secretos na descoberta.

## P3: manutenção

- Atualizar Media3 e target SDK somente com validação Android por versão e regressões de RTSP.
- Não modificar Vigia IA automaticamente. Transferir conclusões técnicas após os testes.

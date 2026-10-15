# Pauta técnica após 1.4.13

## P0: preservação de dados / segurança

- Persistir credenciais RTSP com proteção baseada no Android Keystore e migração sem perda das câmeras legadas.
- **Assinatura permanente configurada em 1.4.13 (não testada no Actions)**. Importar os quatro Secrets, executar build e guardar backup privado. Recuperar a assinatura original se quiser atualizar instalações antigas sem desinstalação.

## P1: robustez

- Adicionar timeout global/cancelamento e lifecycle apropriado para consultas ONVIF/RTSP.
- Recuperar sessões de gravação interrompidas ou descartá-las com confirmação.
- Validar cada octeto de IPv4 e portas entre 1–65535 nos formulários manuais.
- Medir e validar H.264/H.265 e fallback em câmeras reais.

## P2: câmera entre celulares

- Validar correção de prévia preta com TextureView no aparelho que apresentou a falha, alternando câmeras, girando a tela, rolando e retornando do segundo plano.
- Testar transmissão e pareamento QR no mínimo em dois aparelhos distintos, incluindo Wi-Fi sem internet e hotspot.
- Revisar disponibilidade da API Camera1, desempenho, largura de banda, reconexão e consumo energético.
- Não usar HTTP MJPEG em redes inseguras; avaliar TLS, WebRTC ou H.264 para confidencialidade e eficiência.
- Resolver reconexão automática após mudança de IP sem expor códigos secretos na descoberta.

## P3: manutenção

- Atualizar Media3 e target SDK somente com validação Android por versão e regressões de RTSP.
- Não modificar Vigia IA automaticamente. Transferir conclusões técnicas após os testes.

# Pauta técnica após 1.4.15

## P0: preservação de dados / segurança

- Persistir credenciais RTSP com proteção baseada no Android Keystore e migração sem perda das câmeras legadas.
- **Build APK 17 falhou ao ler Base64 do Secret na v1.4.13**, apesar de o original armazenado em backup ser válido. A v1.4.15 tornou o pré-processador tolerante a padding/espaços, mas reimportar o JSON original caso o valor persistido no GitHub esteja corrompido e confirmar um novo build bem-sucedido. Guardar backup privado. Recuperar a assinatura original se quiser atualizar instalações antigas sem desinstalação.

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

## P4: interface 1.4.14

- Validar janelas QR e scanner retrato em dois dispositivos, inclusive MIUI e autorização de câmera.
- Confirmar que ao fechar o QR a transmissão não é interrompida e que a leitura abre o espectador.
- Verificar tamanhos reduzidos, modo noturno e acessibilidade dos controles de 4 ícones.

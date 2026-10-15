# Pauta técnica após 1.4.15

## P0: preservação de dados / segurança

- Persistir credenciais RTSP com proteção baseada no Android Keystore e migração sem perda das câmeras legadas.
- **Builds APK 17 e 18 falharam em KEYSTORE_BASE64**, embora o backup seja válido. A 1.4.16 prioriza o novo KEYSTORE_HEX para evitar caracteres especiais na importação, sem alterar certificado. Importar KEYSTORE_HEX e demais Secrets corretos no GitHub Manager e confirmar build real. Guardar backup privado. Recuperar a assinatura original se quiser atualizar instalações antigas sem desinstalação.

## P1: robustez

- Adicionar timeout global/cancelamento e lifecycle apropriado para consultas ONVIF/RTSP.
- Recuperar sessões de gravação interrompidas ou descartá-las com confirmação.
- Validar cada octeto de IPv4 e portas entre 1–65535 nos formulários manuais.
- Medir e validar H.264/H.265 e fallback em câmeras reais.

## P2: câmera entre celulares

- Validar em hardware a nova 1.4.17: FPS real (durante gravação e com duas câmeras), latência medida ponta-a-ponta, consumo, temperatura, encoder H.264 e MP4 de Downloads.
- Verificar Camera2 concorrente em Android 11+ com `getConcurrentCameraIds()`, múltiplas orientações e tamanhos de quadros. Nem todo par anunciado aceita sessão simultânea com as opções requisitadas.
- Avaliar reconexão automática e troca futura para H.264 de rede, de forma a reduzir banda MJPEG.
- Gravação no celular visualizador, recuperação de MP4 interrompido e gravação contínua em segundo plano não estão implementadas.


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

## Acompanhamento 1.4.26 (RTSP)

- Validar na Câmera 209 que o LibVLC reproduz e não há mais `RtspClient.handleRtspResponse:544` (motor Media3 RTSP suspenso).
- Reavaliar versão Media3 futura com resposta RTSP sem `CSeq` e testes físicos antes de reabilitar a opção. Não reintroduzir fallback assíncrono incapaz de capturar crashes em `HandlerThread`.
- Medir qualidade, latência, consumo e PiP no celular do guidão; não inferir estabilidade por testes estáticos.

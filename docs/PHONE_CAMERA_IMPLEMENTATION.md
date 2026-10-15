# Modo celular como câmera | Minha Câmera 1.4.12+22

## Escopo implementado

- Instale o mesmo APK em **dois celulares Android**. Um funciona como transmissor e outro como monitor.
- **Entrada unificada:** toque em «Usar este celular como câmera» e escolha **Transmitir** ou **Visualizar**. O seletor também vale para a opção equivalente do menu superior.
- **No transmissor:** escolha **Transmitir**, permita CAMERA e mantenha a tela aberta com Wi-Fi conectado. A tela exibe o **QR Code** contendo `http://IP:8765/live/TOKEN`, a prévia local (exibida acima do QR) e botões para alternar a lente, copiar, enviar ou revogar o acesso. O QR é renovado a cada reinício de sessão, mudança de IP ou rotação do token, e ocultado ao parar a transmissão.
- **No receptor:** escolha **Visualizar**, permita acesso à câmera ao leitor ZXing, enquadre o QR do transmissor; um QR válido é cadastrado, se ainda não constar da lista, e o MJPEG abre automaticamente. QR inválido é rejeitado, com opção de nova leitura. Não se abre qualquer link arbitrário que venha de QR.
- **Alternativa manual:** menu «Adicionar outro celular», colar o endereço e salvar. O novo cartão abre um visualizador MJPEG nativo; «Tentar novamente» reconecta.
- Não usa ONVIF, RTSP, Media3 nem LibVLC no caminho MJPEG. As câmeras RTSP/ONVIF existentes continuam em fluxo separado.
- Sem captura de áudio, IA, análise de imagem, gravação de MJPEG, nuvem, conta ou servidor remoto. A gravação RTSP original não foi alterada.

## Modelo técnico

- `PhoneCameraActivity` usa `android.hardware.Camera` (Camera1, legado) para captura e `TextureView` como prévia vinculada por `setPreviewTexture`, evitando `SurfaceView` dentro de `ScrollView`, com correção de proporção da imagem no Android; NV21 com buffers reutilizados, ~4 quadros/s e tamanho preferencial 640×480, escolhido entre resoluções anunciadas pelo hardware. Apenas uma conversão JPEG (qualidade 65); `Nv21Frames` corrige orientação de 0, 90, 180 e 270 graus. Trabalhador único evita codificações simultâneas.
- `PhoneCameraServer`: ServerSocket vinculado somente ao IPv4 da interface Wi-Fi/AP, porta fixa 8765, máximo 2 conexões simultâneas, token aleatório de 128 bits persistente no aparelho (em preferências privadas, sem criptografia) e revogável pelo botão «Trocar código de acesso», resposta `multipart/x-mixed-replace` com `Content-Length`, limites de cabeçalho/frame e desligamento ao sair da Activity. Não expõe URL em logs.
- `PhoneCameraViewerActivity`: HttpURLConnection sem redirecionamento HTTP, limita imagens a 1 MiB, valida MIME, decodifica cada frame JPEG, apresenta em ImageView e fecha a conexão ao sair.
- `PhoneCameraUrls`: aceita apenas HTTP IPv4 privado/link-local, porta explícita e rota `/live/` com token em hexadecimal de 32 caracteres. O resultado do escaneamento passa por essa validação antes de qualquer conexão HTTP.
- `MainActivity`: `registerForActivityResult(ScanContract())`, leitura exclusiva de QR, cadastro sem duplicatas e abertura do receptor. `PhoneCameraActivity` gera Bitmap QR com `BarcodeEncoder` a partir da URL de sessão. Dependência `com.journeyapps:zxing-android-embedded:4.3.0`, compatível com minSdk 24.
- O processo é vinculado à rede Wi-Fi para atender redes locais sem internet. O endereço é identificado por LinkProperties ou por interfaces WLAN/AP compatíveis.
- A URL é estável enquanto o aparelho permanecer no mesmo IPv4 e o token não for revogado. Ao bloquear/sair, a transmissão é interrompida e pode ser retomada usando a mesma URL. Mudança de IP ou renovação do token exige escanear o QR novamente no receptor (o cadastro antigo não é atualizado automaticamente). Melhorias possíveis: descoberta automática e criptografia de transporte.

## Limitações / segurança

- A transmissão é HTTP **sem criptografia**. O **QR Code contém a URL e o token secreto**: qualquer pessoa que fotografá-lo e tiver acesso à mesma rede pode conectar durante a sessão. Use apenas Wi-Fi confiável e não publique o QR ou o endereço. Quem tiver a URL completa enquanto a sessão estiver ativa pode ver a transmissão na mesma rede. O código é aleatório, privado e revogável, mas é reutilizado entre sessões para viabilizar o compartilhamento por outros aplicativos. Não é impresso em logs.
- É necessário que os dois aparelhos se alcancem pela LAN; redes com isolamento de clientes e certas configurações de hotspot podem bloquear o tráfego. A opção de hotspot pode não fornecer uma interface IPv4 identificável em todos os fabricantes.
- A transmissão para ao sair da tela ou bloquear o celular transmissor. Não há serviço de câmera em segundo plano. O aparelho mantém tela ligada enquanto a Activity está ativa; pode haver consumo sensível de bateria, CPU e Wi-Fi. Ao compartilhar o endereço com outro app, a câmera pausa e volta a transmitir quando o usuário retorna à tela. Não há compressão H.264, áudio ou acesso remoto via internet.
- O estado «Transmitindo» só aparece depois de publicar o primeiro JPEG. Se nenhum quadro chegar ou a prévia local não renderizar em 5 s, a tela apresenta diagnóstico específico. A mudança para TextureView **não foi validada em aparelho físico** e não é uma garantia de compatibilidade com todos os fabricantes.
- A API Camera1 é obsoleta, embora compatível com API 24+. Fabricantes que restringem Camera1 podem exigir implementação Camera2/CameraX. A prévia poderá falhar se a câmera estiver ocupada.
- O receptor MJPEG não tem gravação. O cartão salvo preserva somente o endereço, que deixa de funcionar quando a sessão é encerrada.
- O leitor QR depende da resolução das bibliotecas ZXing pelo Gradle durante a compilação Android. **Não foram executados testes físicos nem build Android completo neste ambiente**. A validação funcional depende de dois celulares na mesma rede.

## Testes recomendados em aparelhos reais

1. Instalar assinatura compatível em dois Androids, liberar permissão de câmera nos dois, conectar mesmo Wi-Fi; selecionar Transmitir no A e Visualizar no B, escanear o QR.
2. Verificar imagem, rotação, alternância frontal/traseira e desconexão ao pressionar Home/Power.
3. Confirmar token recusado (`HTTP 403`), leitura de QR inválido sem abrir URL externa, troca de token e novo QR funcional.
4. Testar redes 192.168.x, hotspot, Wi-Fi sem internet, clientes isolados e retorno de rotação.
5. Medir uso de CPU, aquecimento, banda, bateria e comportamento durante chamadas/interrupções.
6. Verificar que câmeras RTSP existentes, seus cadastros e gravações não sofreram regressão.

## Roteiro de correções futuras

P0: preservar continuidade de assinatura dos APKs existentes, recuperar chave original sem substituí-la; criptografar credenciais RTSP existentes com migração sem perda.
P1: cancelar testes ONVIF/RTSP demorados, recuperar gravações abandonadas, validar efetivamente IP/portas e testar fluxos H.264/H.265 reais.
P2: integrar câmera Android pelo protocolo H.264/RTSP ou WebRTC para reduzir banda e consumo; descoberta automática sem escaneamento, Camera2/CameraX, autenticação forte e criptografia de transporte, melhor UX de reconexão.
P3: atualizar Media3 e target SDK com testes de regressão. Essas melhorias **não** estão implementadas nesta versão.

## Integração com Vigia IA

`PhoneCameraServer`, `PhoneCameraUrls`, `Nv21Frames` e o visualizador MJPEG podem ser referências técnicas, mas não devem ser copiados sem adaptar lifecycle, política de energia, segurança e protocolos. A função câmera deve permanecer opcional/inativa fora de uso. O projeto Vigia IA não foi alterado.

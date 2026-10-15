# Modo celular como câmera | Minha Câmera 1.4.12+22

## Escopo implementado

- Instale o mesmo APK em **dois celulares Android**. Um funciona como transmissor e outro como monitor.
- **Entrada unificada:** toque em «Usar este celular como câmera» e escolha **Transmitir** ou **Visualizar**. O seletor também vale para a opção equivalente do menu superior.
- **No transmissor:** escolha **Transmitir**, permita CAMERA e mantenha a tela aberta com Wi-Fi conectado. A tela exibe o **QR Code** contendo `http://IP:8765/live/TOKEN`, a prévia local (exibida acima do QR) e botões para alternar a lente, copiar, enviar ou revogar o acesso. O QR é renovado a cada reinício de sessão, mudança de IP ou rotação do token, e ocultado ao parar a transmissão.
- **No receptor:** escolha **Visualizar**, permita acesso à câmera ao leitor ZXing, enquadre o QR do transmissor; um QR válido é cadastrado, se ainda não constar da lista, e o MJPEG abre automaticamente. QR inválido é rejeitado, com opção de nova leitura. Não se abre qualquer link arbitrário que venha de QR.
- **Alternativa manual:** menu «Adicionar outro celular», colar o endereço e salvar. O novo cartão abre um visualizador MJPEG nativo; «Tentar novamente» reconecta.
- Não usa ONVIF, RTSP, Media3 nem LibVLC no caminho MJPEG. As câmeras RTSP/ONVIF existentes continuam em fluxo separado.
- Sem captura de áudio, IA, análise de imagem, nuvem, conta ou servidor remoto. Desde 1.4.17 há gravação MP4 no transmissor apenas, sem áudio, em Downloads; a gravação RTSP original não foi alterada.

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
- O receptor MJPEG ainda não tem gravação, embora o transmissor tenha MP4 desde 1.4.17. O cartão salvo preserva somente o endereço, que deixa de funcionar quando a sessão é encerrada.
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

## Atualização 1.4.17

- Barras e ações: Virar, Dupla, QR, Gravar/Parar, Mais; QR mantém janela modal.
- MJPEG: captura limitada a no mínimo 90 ms de intervalo em vez de 250 ms, compressão JPEG 62%; receptor usa fila UI de um único quadro, substituindo frames atrasados. Não há garantia fixa de FPS ou delay.
- Dupla experimental: Camera2 concorrente Android 11+, frontal e traseira empilhadas, com fallback para câmera única quando não suportado. Compatibilidade em hardware pendente.
- MP4: MediaCodec AVC ByteBuffer + MediaMuxer em worker exclusivo da gravação; exportação após finalizar, Android 10+ via MediaStore Downloads, Android 7–9 com permissão de armazenamento. Sem áudio ou execução em segundo plano. Vídeo intermediário de sessão é apagado após exportação; encerramento abrupto não oferece recuperação.
- Testes físicos de gravação, codec, economia de energia, duas lentes e latência pendentes.

## Telemetria 1.4.18 (observações para adaptação)

O quadro NV21 recebe selo de carga e temperatura da **bateria do transmissor** antes do MJPEG/MP4. Como é uma marca d'água gravada no vídeo, o receptor não precisa de endpoint de telemetria. Para alternar sem gravar dados no vídeo em uma futura arquitetura WebRTC, considerar canal de dados autenticado e overlay apenas no cliente. Leituras provêm do broadcast sticky `ACTION_BATTERY_CHANGED` e não representam a CPU.

## Menu Mais renovado (1.4.28+38)
A Activity transmissora agora usa um diálogo customizado em vez de uma lista simples do AlertDialog. As ações Link, Código e Gravações exibem ícones e descrições curtas; as opções Bateria no vídeo, Eco e Tela apagada usam switches visuais com estado imediato.

## Modo persistente com tela apagada (1.4.27+37)

O serviço `PhoneCameraBackgroundService` é iniciado exclusivamente enquanto a tela `PhoneCameraActivity` está visível após consentimento explícito em **Mais**. O Android 14 exige permissões `FOREGROUND_SERVICE_CAMERA` e `CAMERA` e proíbe a criação do serviço de câmera a partir do plano de fundo. Com o serviço ativo, o `onPause` da Activity não para a captura; `SurfaceTexture` e `Camera` permanecem pertencendo ao serviço, com `START_NOT_STICKY` e notificação de encerramento/REC. O serviço mantém wake lock parcial e Wi-Fi lock durante a sessão.

**Escopo:** uma câmera de cada vez com API `android.hardware.Camera` legada, 640x480 aproximado sujeito a tamanhos suportados, MJPEG e H.264/MP4 sem áudio. A prévia na Activity decodifica a amostra JPEG a 1 FPS enquanto está visível; durante o bloqueio não ocorre decodificação de interface. A gravação pode ser ligada/desligada no serviço antes de apagar a tela, sem interromper a rede. Ao parar, o muxer H.264 deve terminar antes de remover a notificação foreground.

**Riscos a medir:** determinados HALs OEM podem parar o preview após a tela apagar ou quando a fila SurfaceTexture sem GL não é consumida; permissões de notificação podem esconder controles. Validar em Redmi A3 e outro receptor com intervalos prolongados e medir temperatura da bateria, taxa de frames, consumo e arquivo MP4. A câmera dupla permanece dependente da Activity e não é suportada no serviço.

A 1.4.27 adiciona REC remoto por POST (mesmo token privado usado para a transmissão) no modo persistente. Rotas não autenticadas respondem HTTP 403; o comando não fica exposto por CORS. Os controles aparecem em **Assistir → Mais** e são idempotentes. A transmissão não é reiniciada ao alternar REC. Em redes Wi-Fi compartilhadas, HTTP não cifra o token: usar apenas hotspot/rede local de confiança.

## Visualizador e Eco remoto (1.4.29+39)
O receptor exibe dados compactos em cabeçalho/card, sem duplicar a camada sobre o vídeo no modo normal; no PiP, conserva etiqueta compacta. O menu Mais foi renovado. O comando Eco via HTTP POST em /control/<token>/eco/enable|disable só está disponível quando o foreground service do transmissor está ativo; o token é o mesmo da URL MJPEG validada como IPv4 local. Não existe comando de ligar a câmera remotamente a partir do estado totalmente parado. A foto captura o JPEG recebido e salva no receptor sem retorno do conteúdo ao transmissor.

## Serviço de captura em tela apagada e controle remoto (1.4.30+40)
A prévia local não inicia MJPEG até o usuário tocar Iniciar transmissão. O serviço `PhoneHeadlessPreview` cria EGL pbuffer com SurfaceTexture e chama updateTexImage em cada frame no HandlerThread para esvaziar BufferQueue sem desenhar. O HTTP privado ganhou foto JPEG nativa, qualidade/fps e troca de câmera. A foto é salva no próprio transmissor e interrompe brevemente preview; durante REC os comandos destrutivos retornam conflito. O receptor mostra o FPS real e dimensões JPEG, não infere 720p. Testar no aparelho após build.

## H.264 experimental e diagnóstico do modo apagado (1.4.32+42)
No transmissor, MJPEG é o formato de compatibilidade em `/live/TOKEN`; o modo H.264 usa MediaCodec AVC ByteBuffer com quadros NV21 convertidos para YUV420 e entrega NALs Annex-B via HTTP `/h264/TOKEN`. A recepção usa LibVLC com `demux=h264`. Alternar é autenticado via POST e o receptor consulta `GET /control/TOKEN/status`; os dois devem estar atualizados. Encoders incompatíveis ou sem suporte acelerado recuam para MJPEG, mas é obrigatório testar MediaCodec+LibVLC no Redmi A3.

A prévia de segundo plano possui contadores de callbacks `SurfaceTexture`, `updateTexImage()`, erro EGL, última captura NV21 e última publicação, persistindo eventos importantes em `private_stream_diagnostics`. A tela exibe relógio de transmissão independente do REC. Requer testes em aparelho: rede Wi-Fi, bloqueio real, PiP, 720p/30 FPS, gravação e temperatura.

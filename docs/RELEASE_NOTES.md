# Minha Câmera 1.4.32 (versionCode 42)

- Transmissão H.264 experimental com MediaCodec/LibVLC via HTTP local autenticado; MJPEG mantido como compatibilidade e fallback.
- Seletor de codec visível nos dois aparelhos, incluindo comando remoto autenticado.
- Configuração de resolução/FPS da transmissão e FPS/bitrate do MP4; qualidade JPEG normal aumentada conservadoramente.
- Cronômetro de transmissão AO VIVO independente de gravação MP4.
- Diagnóstico de câmera com tela apagada: callbacks NV21, buffers EGL, tempo sem quadros, erros de codec e reinicializações; registros locais limitados e consultáveis remotamente com o token.
- Compilação Android, reprodução H.264 e estabilidade real no Redmi A3 pendentes de testes físicos.

# Minha Câmera 1.4.31 (versionCode 41)

- Correção dos quatro erros de compilação Kotlin confirmados pelo workflow Build APK 31.
- Tratamento seguro da sessão ativa nos callbacks do serviço e de valores opcionais nos seletores remotos.
- Mantidas transmissão manual, REC independente, foto, resolução/FPS remotos, troca de câmera, tela apagada e PiP.
- Compilação APK da 1.4.31 ainda não confirmada; executar GitHub Actions.

# Minha Câmera 1.4.30 (versionCode 40)

- Modo transmissor inicia em prévia. Pressione **Iniciar transmissão**; REC é separado e sempre opcional.
- Corrigido o fluxo de consumo dos buffers EGL no serviço de câmera com tela apagada.
- Foto JPEG na maior resolução anunciada pela câmera, salva no próprio transmissor; não aceita foto durante REC.
- Perfis remotos de resolução e FPS e troca frontal/traseira, com token privado na rede local.
- Cronômetro de gravação no visualizador, ícones revisados e tela cheia com controles transparentes.
- Janela de novidades uma vez por versão instalada.
- APK, estabilidade térmica, reprodução e imagens reais ainda precisam de validação em hardware.

# Minha Câmera 1.4.29 (versionCode 39)

- Visualizador de câmera remota renovado, alinhado ao painel de referência.
- Card de vídeo escuro, estado de conexão, temperatura, bateria, FPS e resolução efetivamente recebida.
- Controles Gravar/Parar remoto, Foto local do frame recebido, Girar 180°, Expandir, PiP, Inteira, Espelho e Reconectar.
- Eco remoto com token de acesso no modo de tela apagada; feedback de indisponibilidade para servidor legado.
- Menu Mais com ícones, switches e detalhes técnicos opcionais.
- Gravação e Eco remotos exigem a versão atual no transmissor e modo de captura persistente. A foto é salva no receptor.
- Não inclui comandos não implementados (zoom, lanterna, qualidade, FPS remoto, trocar câmera).

# Minha Câmera 1.4.28 (versionCode 38)

- Pop-up **Mais** do transmissor renovado com ícones, subtítulos e switches para ações liga/desliga.
- Link, Código e Gravações ficaram mais organizados e fáceis de entender.
- Ajustes de bateria no vídeo, modo Eco e tela apagada mostram o estado visual no próprio pop-up.

# Minha Câmera 1.4.27 (versionCode 37)

- Modo persistente de uma câmera: **Celular → Transmitir → Mais → Manter transmitindo com tela apagada**. Inicia um serviço em primeiro plano do tipo `camera` enquanto o aplicativo está visível, permitindo bloquear a tela e manter MJPEG pela mesma porta/token local. A prévia local passa a exibir amostras do serviço, sem reabrir a câmera na Activity.
- Gravação MP4 **opcional**: após ativar o modo persistente, toque em **Gravar** antes de bloquear o aparelho ou use o controle da notificação quando disponível. A gravação termina e salva ao tocar em **Parar** ou encerrar o serviço. Recepção PiP no outro celular continua.
- O visualizador pode iniciar/parar REC remotamente em **Assistir → Mais** quando o transmissor utiliza o serviço persistente. A rota POST é autenticada por token de 32 caracteres, sem nuvem. Sem HTTPS, manter estritamente na rede local confiável.
- Indicador compacto do transmissor `🔋 77% · 39,0°C`, com raio apenas durante carregamento; no receptor, **Mais → Indicadores do vídeo e PiP** permite selecionar porcentagem, temperatura e raio. O selo deixa de ser queimado em MJPEG e MP4.
- Android 14+: requer permissão de câmera e serviço iniciado enquanto a Activity está visível; notificação pode depender de autorização no Android 13+. Disponibilidade de captura contínua e ganho de bateria devem ser medidos no Redmi A3; Android pode encerrar serviços por políticas térmicas/energia. Câmera dupla não foi migrada para segundo plano.
- Conferidos código Kotlin, recursos, regressões e versões localmente; build APK e teste físico pendentes.

# Minha Câmera 1.4.26 (versionCode 36)

- **Correção do fechamento RTSP:** o registro recebido apontou `NullPointerException` em Media3 1.3.1 (`RtspClient.java:544`), ao tentar processar uma resposta RTSP sem `CSeq` disponível. O erro ocorre na thread de reprodução e o fallback anterior não conseguia interceptá-lo.
- Reprodução RTSP, nova tentativa e URLs alternativas iniciam diretamente em LibVLC 3.7.7. Seleção manual de Media3 desabilitada temporariamente para evitar o caminho identificado como fatal.
- PiP para RTSP/MJPEG, gravação RTSP por LibVLC, câmeras salvas, descoberta, versão universal/ABIs e assinatura preservados.
- A causa da resposta incompleta e a compatibilidade com a câmera real precisam de teste. Sem compilação Android ou teste físico nesta entrega.

# Minha Câmera 1.4.25 (versionCode 35)

- PiP nativo para recepção MJPEG em **Celular → Assistir → Mais → Janela flutuante** e para câmera IP RTSP pelo botão **PiP**; sem nova permissão especial.
- Leitura MJPEG contínua enquanto PiP visível, preservando modo espelho e descartando quadros antigos. Encerramento normal ao fechar a janela.
- Tratamento de `LinkageError` de inicialização Media3 para tentar fallback LibVLC; relatório local copiável de crashes Java/Kotlin no próximo início para investigar fechamento RTSP relatado em vídeo.
- Não é uma correção comprovada para fechamento nativo do player. Necessário Logcat se ocorrer de novo.
- Android build, playback/compatibilidade PiP e aparelhos físicos ainda não validados.

# Minha Câmera 1.4.24 (versionCode 34)

- No receptor, Espelho inverte a visualização horizontalmente sem alterar o vídeo enviado, e lembra a configuração.
- Foco amplia a prévia e oculta controles secundários; toque na imagem para retornar ao layout normal.
- Imagem antiga é limpa quando o Wi-Fi cai, evitando confusão com vídeo ao vivo.
- Cinco cartões compactos seguem a linguagem visual existente. MJPEG, assinatura e recursos anteriores preservados.

# Minha Câmera 1.4.23 (versionCode 33)

- Corrigido Build APK 24: recursos Android rejeitavam apóstrofo não escapado em `novidades_text`.
- Adicionado teste preventivo de escapes e caracteres especiais dos recursos XML na etapa de verificação antes do Gradle.
- Assinatura permanente, protocolo MJPEG com telemetria e interface de retrovisor mantidos.

# Minha Câmera 1.4.22 (versionCode 32)

- Corrigida a compilação dos testes de protocolos após integrar a telemetria do receptor na 1.4.21.
- Dados de bateria agora usam modelo Kotlin independente das APIs Android, compilável também no teste JVM.
- Adicionados testes para os cabeçalhos de bateria, temperatura, status de carregamento e corrente no fluxo MJPEG.
- Mantidos os recursos de retrovisor, leitura por QR, modo Eco e assinatura estável.

# Minha Câmera 1.4.21 (versionCode 31)

- Receptor redesenhado em modo retrato com visual próximo ao “Ao vivo” do transmissor.
- Telemetria estendida do transmissor no receptor e na marca d'água: bateria, temperatura, carregando/não carregando e consumo médio do dia.
- Tela receptora sem o elemento central indesejado e mais adequada para uso como retrovisor no guidão.

# Minha Câmera 1.4.20 (versionCode 30)

- **Modo Eco** em Mais, para celulares de menor desempenho: reduz FPS e a qualidade dos JPEGs enviados em tempo real.
- Alerta para aquecimento da **bateria**, com redução de envio em temperatura alta. A leitura pode não estar disponível em todos os aparelhos; não é temperatura da CPU.
- Barra Virar/Dupla/QR/Gravar/Mais, player, modo duplo experimental, gravação e assinatura permanente preservados.
- Sem bibliotecas ou permissões novas; compilação e teste físico ainda pendentes.

# Minha Câmera 1.4.19 (versionCode 29)

## FPS real e reconexão automática, sem aumentar dependências

- Transmissor conta quadros MJPEG publicados; receptor conta quadros efetivamente mostrados. Interface compacta mostra FPS somente a cada 2 segundos.
- Transmissão MJPEG inclui timestamps monotônicos autenticados pelo mesmo caminho/token da câmera. Receptor usa uma amostra do relógio do transmissor obtida na resposta HTTP para apresentar **atraso aproximado de entrega após gerar JPEG**. Não é medição exata da latência câmera-tela; diferença de relógios, rede e buffers afetam a estimativa. Ausência de amostra válida = `--`.
- Em perda da conexão, receptor reconecta automaticamente com backoff de 1/2/4/8 s (máximo). Encerramento da tela interrompe imediatamente as tentativas. Token incorreto exige novo QR ou endereço correto e não gera reconexões infinitas.
- Sem pacote de terceiros novo, sem encode extra, sem análise de imagem, mantendo todas as funções anteriores, assinatura e Secrets.

**Limites:** servidor transmissor precisa permanecer com a tela ativa; mudança do IP local exige novo QR/endereço. Compilação Android, medição real e reconexão em dois aparelhos pendentes.

---

# Minha Câmera 1.4.18 (versionCode 28)

## Telemetria de bateria incorporada à transmissão

- Novo selo compacto `Bateria 61% | Bat. 34,7°C` (exemplo) no topo do vídeo do transmissor, recebido pelo outro celular e incorporado aos MP4 gravados.
- A temperatura é **da bateria do transmissor**, não CPU, sensor ambiente ou do receptor. Não há valor falso quando o sistema deixa de informar o dado (`--`). Atualização consultada a cada 15 segundos.
- A prévia da câmera única tem um indicador local correspondente. Menu **Mais** permite ocultar/mostrar o selo e guarda a escolha para próximas sessões.
- Quadro NV21 recebe o selo antes do JPEG e antes da codificação H.264, mantendo só uma compressão JPEG por frame. Sem rede adicional, permissão nova ou envio de telemetria separada.
- Preservados: navegação visual, QR pop-up, MJPEG adaptado, câmera dupla experimental, gravação `Downloads/MinhaCamera`, assinatura e Secrets (sem valores privados no ZIP).

**Verificação:** validação estática de Kotlin/XML, testes JVM dos cálculos de bateria e integração, testes de assinatura e configuração. Android real, gravação efetiva e Gradle completo continuam pendentes.

---

# Minha Câmera 1.4.17 (versionCode 27)

**Nova tela Ao vivo:** barra Virar · Dupla · QR · Gravar/Parar · Mais conforme design solicitado. O QR permanece em pop-up e o menu Mais contém Link e Código.

**Transmissão:** limite MJPEG reduzido de 250 ms para 90 ms por captura (máximo teórico ~11 FPS). Receptor mostra os quadros mais recentes, evitando a fila de renders atrasados. O desempenho e a latência reais dependem do celular/Wi-Fi.

**Câmera dupla experimental:** tenta câmera traseira acima e frontal abaixo usando Camera2, somente se o Android 11+ anunciar concorrência frontal/traseira. Falha e incompatibilidade retornam à câmera única.

**Gravação do transmissor:** MP4 H.264 sem áudio em `Downloads/MinhaCamera`, pelo MediaStore em Android 10+; Android 7–9 requer autorização de armazenamento. O codificador roda em um executor próprio, gravação termina ao sair da tela; em falha de codec/armazenamento o usuário é informado. Sem garantia de gravação em segundo plano nem recuperação de vídeo não finalizado.

**Validação:** XML, metadados, assinatura sem alterações, verificações Kotlin offline e testes específicos de quadros. Ainda faltam `assembleRelease` no GitHub Actions e ensaios em dois aparelhos, incluindo o modo Dupla e saída MP4. Não trocar a chave privada.

# Minha Câmera 1.4.16 (versionCode 26)

**Correção do Build APK 18:** novamente o GitHub Actions recebeu `KEYSTORE_BASE64` contendo caracteres inválidos e interrompeu o job antes do Gradle. O conteúdo real do Secret não é visível no log. O backup privado original da keystore e dos importadores é válido.

- O novo `KEYSTORE_HEX` permite importar os mesmos bytes do arquivo PKCS12 usando somente caracteres de baixo risco para o parser. **Importe o arquivo PRIVADO v1.4.16 de Secrets no GitHub Manager antes de executar o workflow.**
- O workflow usa HEX prioritariamente e preserva compatibilidade opcional com Base64 legado; não regenera o certificado nem usa Debug.
- Os testes verificam os dois formatos, preferência do HEX e rejeição de dados inválidos. Testes Python de QR e interface aprovados; suíte Kotlin de protocolos atingiu o tempo limite nesta rodada.
- O build Android completo e a atualização real entre APKs ainda não foram executados neste ambiente.

# Minha Câmera 1.4.15 (versionCode 25)

## Correção do Build APK 17: Secret Base64 inválido

O Build APK 17 falhou antes do Gradle no passo de preparação da assinatura da versão 1.4.13+23: `base64: invalid input`. A execução encontrou os quatro nomes de Secrets, mas não pôde decodificar o `KEYSTORE_BASE64`. O TXT/JSON original é válido; não é possível garantir pelos logs o conteúdo que ficou armazenado no GitHub.

- Preparador robusto em Python normaliza whitespace, aspas externas e `=` ausente, valida PKCS12 e verifica o mesmo fingerprint SHA-256 antes de publicar qualquer keystore para o Gradle.
- Secrets realmente alterados/truncados continuam bloqueando o build e devem ser substituídos por meio do importador JSON original do GitHub Manager.
- Nenhuma chave nova foi criada; todos os APKs continuam usando o mesmo certificado e versionamento sequencial.
- Os recursos da v1.4.14 (Câmeras/Celular, pop-ups QR, prévia e reprodução) foram preservados.
- Testes locais de decodificação e certificado aprovados; compile/assinatura Android no GitHub Actions e atualização no celular pendentes.

# Minha Câmera 1.4.14 (versionCode 24)

## Novidade principal: interface redesenhada

- Barra inferior com dois menus, Câmeras e Celular, com listas separadas e cartões Transmitir/Assistir.
- Tela Ao vivo com prévia local e quatro ações compactas: Virar, QR, Link e Código.
- QR sob demanda e leitor incorporado em pop-ups centralizados, modo retrato.
- Link reúne copiar/enviar sem remover recursos anteriores; servidor MJPEG, RTSP/ONVIF e assinatura estável preservados.
- Build Android e validação em aparelhos reais ainda pendentes.

## Histórico: Minha Câmera 1.4.13 (versionCode 23)

Data: 2026-10-14.

**Assinatura permanente:** APKs Release passam a exigir os quatro Secrets KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS e KEY_PASSWORD. O workflow verifica o certificado SHA-256 fixado no projeto e não usa a chave Debug temporária. A assinatura deve ser preservada para todas as atualizações futuras.

**ATENÇÃO:** a chave desta versão é nova. A instalação existente só poderá atualizar diretamente se tiver o mesmo certificado, o que **não foi comprovado**. Não desinstale antes de salvar os dados importantes; recuperar a chave original é a única alternativa de atualização normal que preserve a instalação com assinatura anterior diferente.

Nenhuma mudança funcional no modo câmera, QR, RTSP ou gravação. Compilação Android e testes de atualização no dispositivo continuam pendentes. Veja [ASSINATURA_PERMANENTE.md](ASSINATURA_PERMANENTE.md).

---

# Minha Câmera 1.4.12 (versionCode 22)

Data: 2026-10-14.

**Correção da prévia preta no celular transmissor:** a visualização da própria câmera mudou de SurfaceView para TextureView, para funcionar de forma mais confiável dentro da tela rolável. A prévia aparece antes do QR Code, sem distorção de proporção. As orientações e recursos de Transmitir/Visualizar permanecem.

**Diagnóstico:** o estado «Transmitindo pela rede local» depende de um quadro JPEG publicado; se não houver quadros ou se a textura local não desenhar o vídeo após cinco segundos, aparece uma mensagem que permite distinguir essas duas falhas.

**Validação:** testes locais estáticos e de protocolos não substituem compilação nem execução no aparelho afetado. Sem atualização na assinatura: verifique o certificado antes de instalar sobre o aplicativo existente. Não desinstale sem proteger dados locais.

Mais detalhes em [VALIDATION_1.4.12.md](VALIDATION_1.4.12.md).

---

# Minha Câmera 1.4.11 (versionCode 21)

Data: 2026-10-14.

**Novo:** «Usar este celular como câmera» oferece **Transmitir** e **Visualizar**. Em Transmitir, QR Code do endpoint MJPEG aparece junto à prévia. Em Visualizar, o aplicativo acessa a câmera para ler QR, verifica o formato e a rede local, guarda o endereço novo na lista e abre o vídeo ao vivo automaticamente. É preciso que os dois celulares tenham acesso mútuo pela mesma rede Wi-Fi ou ponto de acesso funcional.

**Segurança:** o QR inclui a chave de acesso de 128 bits; qualquer pessoa na mesma rede que obtiver o código poderá assistir durante transmissão. Evite mostrá-lo a terceiros e use «Trocar código de acesso» se houver exposição. Somente URLs locais do formato `/live/TOKEN` são aceitas; links QR externos não são abertos. O transporte HTTP não é criptografado. Continua sem áudio, IA ou gravação no modo celular.

**Compatibilidade:** «Adicionar outro celular» por URL, copiar e compartilhar URL continuam possíveis. Versões anteriores podem consumir a mesma URL manualmente, mas não têm leitor de QR. Nova dependência ZXing Android Embedded 4.3.0 para codificar/ler QR (API 24+).

**Verificação:** testes de código e protocolo locais, sem build Android completo nem teste físico de leitura QR/rede. Assinatura preexistente mantida; atualização direta sobre APK já instalado depende da chave de assinatura correspondente.

Detalhes em [PHONE_CAMERA_IMPLEMENTATION.md](PHONE_CAMERA_IMPLEMENTATION.md) e [VALIDATION_1.4.11.md](VALIDATION_1.4.11.md).

---

# Minha Câmera 1.4.10 (versionCode 20)

Data: 2026-10-14.

**Novo:** um Android pode transmitir a própria câmera a outro Android pela rede local. Emissor: «Usar este celular como câmera», permitir câmera, copiar URL e manter a tela aberta. Receptor: «Adicionar outro celular» e colar a URL. Preview MJPEG com câmera traseira/frontal; sem IA, áudio ou gravação neste modo.

**Segurança:** token aleatório de 128 bits persistente no aparelho e revogável, 2 clientes no máximo, somente IPv4 Wi-Fi/AP, endpoint HTTP sem TLS. A mesma URL funciona em novas sessões no mesmo IP até o token ser renovado; em caso de troca de IP, atualizar o cartão no monitor. Usar apenas rede confiável.

**Preservado:** RTSP, ONVIF, H.264/H.265, Media3/LibVLC e gravações existentes; não foram introduzidas alterações nas chaves de assinatura. Build Android e testes físicos pendentes.

Detalhes, roteiro e validações: [PHONE_CAMERA_IMPLEMENTATION.md](PHONE_CAMERA_IMPLEMENTATION.md).

---

# Minha Câmera 1.4.9 (versionCode 19)

Data: 2026-10-11.

Correção de rotação: controles reorganizados em uma única linha na horizontal sem recriar a Activity, desconectar o player ou interromper a gravação. A barra superior fica mais compacta; ícones acompanham o texto.

Lentes superior e inferior: enquadramento pela proporção real do vídeo, sem zoom. Toda a metade escolhida permanece visível, centralizada com barras quando necessário. Imagem inteira também usa FIT. Não é possível mostrar um vídeo vertical preenchendo toda uma tela horizontal sem cortar ou deformar: barras preservam a imagem.

Menu Identificar câmera e serviços: consulta GetDeviceInformation, GetServices e GetCapabilities. Exibe identidade informada pelo firmware e serviços anunciados de PTZ, gravação, busca e replay. Consulta somente leitura, sem mover a câmera. Não determina qual lente se move, não substitui teste PTZ e não implementa o histórico proprietário do Yoosee. Ausência na resposta não comprova falta do recurso.

ONVIF, RTSP, H.264/H.265, Media3, LibVLC, gravação local, cinco variantes APK, minSdk e configuração de assinatura preservados. Resultados e limitações em PLAYER_FIT_VALIDATION.md.

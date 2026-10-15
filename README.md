# Minha Câmera

**Versão atual: 1.4.27 (versionCode 37)** · Kotlin / Android · Projeto auxiliar de investigação técnica para o **Vigia IA**. Veja [integração técnica com o Vigia IA](docs/VIGIA_IA_INTEGRATION.md).

Desde a **1.4.13**, o projeto exige assinatura Release permanente por Secrets fixos do GitHub Actions e impede publicação com outro certificado. A chave privada fica fora do repositório. Consulte [assinatura permanente](docs/ASSINATURA_PERMANENTE.md) antes de instalar: a assinatura nova não é compatível com APKs antigos assinados por chave diferente.

A **1.4.12** corrige a área preta na prévia do transmissor reportada em Android: substitui `SurfaceView` dentro de `ScrollView` por `TextureView` ligado à câmera e adapta o enquadramento à rotação, sem esticar a imagem. A prévia aparece antes do QR Code. O texto «Transmitindo» agora depende de a câmera realmente produzir e publicar um JPEG; após 5 segundos sem quadros ou sem desenhar a prévia, o app indica o problema. **Ainda precisa de teste físico** para confirmar a correção no aparelho afetado.

A 1.4.11 adiciona **pareamento por QR Code**: ao tocar em «Usar este celular como câmera», escolha **Transmitir** ou **Visualizar**. O transmissor mostra um QR Code sob demanda com a URL local e chave de acesso; o receptor abre o leitor de QR, valida o código, cadastra o dispositivo (sem duplicar endereços já existentes) e abre a imagem ao vivo. É necessário que os aparelhos estejam na mesma rede Wi-Fi (ou numa rede local em que possam se comunicar). Não há nuvem, IA ou áudio. A gravação MP4 a partir da captura de vídeo foi adicionada na 1.4.17, sem som e apenas durante a tela ativa. A transmissão para ao fechar/bloquear a tela do transmissor. A alternativa «Adicionar outro celular» por URL permanece disponível. O QR precisa ser escaneado novamente após mudança de IP ou revogação da chave. Veja [implementação e limites](docs/PHONE_CAMERA_IMPLEMENTATION.md).

A 1.4.9 reorganiza os controles ao girar o celular, mostra cada lente inteira sem zoom e adiciona **Identificar câmera e serviços** ao menu do player. A identidade e os serviços são os informados pelo ONVIF; não identifica qual lente tem motor nem implementa histórico do Yoosee. Veja [validação 1.4.9](docs/PLAYER_FIT_VALIDATION.md).

Aplicativo para cadastrar e reproduzir câmeras IP RTSP. A versão 1.4.8 organiza o cadastro RTSP em lista, renova o player e adiciona gravação local e biblioteca por dia. Mantém APKs por arquitetura, ONVIF, RTSP, H.264/H.265, Media3 e LibVLC. Consulte [CHANGELOG.md](CHANGELOG.md), [notas de release](docs/RELEASE_NOTES.md) e [validação do build](docs/BUILD_VALIDATION.md).

## Janela flutuante PiP (1.4.25)

No receptor **Celular → Assistir**, aguarde a primeira imagem e escolha **Mais → Janela flutuante (PiP)**. No player RTSP, use o botão **PiP** na barra inferior (ou o menu), após aparecer o primeiro quadro e antes de iniciar qualquer gravação. Em seguida é possível abrir o app de mapas com o vídeo visível. No PiP, o receptor MJPEG mantém leitura e reconexão enquanto sua Activity permanece visível; ao fechar o PiP, interrompe a rede. O transmissor NÃO passa a transmitir com a tela apagada nem em segundo plano. Requer Android 8+ e suporte do fabricante ao PiP; sem permissão "Exibir sobre outros apps". O uso real em Xiaomi/HyperOS é pendente.

**Modo com tela apagada 1.4.27:** em **Celular → Transmitir → Mais → Manter transmitindo com tela apagada**, o aplicativo transfere a câmera única para um `ForegroundService` de tipo `camera`, usando uma `SurfaceTexture` independente da Activity. A transmissão MJPEG continua ao bloquear a tela, com gravação MP4 opcional pelo botão Gravar ou ação da notificação, e parada manual na notificação ou em Mais. A gravação deve ser iniciada no modo persistente, antes de bloquear a tela. Dual Camera não é suportada no modo persistente. O Android pode interromper o serviço por restrições do fabricante, bateria ou temperatura, então o Redmi A3 deve ser testado fisicamente. Não reinicia automaticamente após o sistema encerrar o processo.

**Indicadores:** a bateria e a temperatura vêm dos cabeçalhos MJPEG, sem serem incorporadas aos pixels do vídeo ou MP4. O receptor exibe somente `🔋 77% · 39,0°C` e acrescenta `⚡` quando detecta carregamento. Em **Assistir → Mais → Indicadores do vídeo e PiP**, bateria, temperatura e raio podem ser ativados individualmente. Não exibe corrente média nem texto "sem carga" dentro do PiP. O selo local no transmissor também é apenas uma View.

**Correção RTSP 1.4.26**: o log identificou `NullPointerException` em `RtspClient.java:544` (Media3 1.3.1), ligada à tentativa de usar `CSeq` de uma resposta RTSP sem o campo disponível. Agora o app usa **LibVLC diretamente** para vídeo IP, novas tentativas e URLs alternativas; a opção Media3 fica temporariamente indisponível, pois sua falha em thread interna escapava do fallback. O Media3 permanece no código para investigação futura, mas não é iniciado no fluxo normal. Continuamos sem captura dos pacotes do servidor e sem teste físico de câmera. O registro privado de crashes Java/Kotlin é mantido, sem URLs/senhas.

## Descoberta automática

- **ONVIF WS-Discovery:** envia probes multicast UDP a `239.255.255.250:3702`, recebe `ProbeMatch`, identifica o IP de quem respondeu e aproveita os `XAddrs` para consultar o serviço da câmera com credenciais.
- **Varredura TCP complementar:** usa prefixo/máscara IPv4 obtidos do Wi-Fi para testar 554, 8554, 10554, 8555 e 7447. Consolida por IP sem perder outras portas do mesmo dispositivo.
- **Proteção da rede:** limita a concorrência a 20 tarefas; em sub-redes com mais de 1022 hosts, varre por TCP apenas o bloco /24 local, informando essa limitação ao usuário.
- **Fallback:** se não encontrar dispositivos, realiza uma segunda rodada. Multicast depende das regras de roteador, isolamento do Wi-Fi e configuração ONVIF da câmera.

## Recursos de câmera

- **Consulta ONVIF Media:** com usuário e senha, consulta os perfis e URLs RTSP usando a URL anunciada na descoberta ou portas ONVIF conhecidas.
- **RTSP:** tenta caminhos comuns para Intelbras, Hikvision, Tapo e outras, com autenticação Digest/Basic. Lê respostas em bytes conforme `Content-Length`.
- **Manual:** permite cadastrar IP/porta/caminho ou URL RTSP completa pelo botão `+`.
- **Player:** usa LibVLC 3.7.7 diretamente para RTSP (H.264/H.265 conforme suporte real). Media3 RTSP 1.3.1 está temporariamente suspenso depois de uma falha fatal de parsing de `CSeq`. O menu não permite reativá-lo nesta release; código e dependência são mantidos para revisão técnica posterior. Falhas H.265 ainda são possíveis dependendo do SDP, codec e aparelho.
- **Codec por SDP:** interpreta `RTSP DESCRIBE`; prioriza o secundário H.264 somente quando confirmado, em vez de assumir que o nome do caminho indica o codec.
- **Histórico:** menu superior "Novidades e versões".

## Como usar

1. Ligue a câmera e conecte o telefone ao mesmo Wi-Fi, sem isolamento entre clientes.
2. Abra **Buscar câmeras** ou **+** para configurar manualmente.
3. Toque na câmera encontrada, informe as credenciais RTSP/ONVIF e teste. Os formulários permitem ver, copiar e colar a senha e escolher sugestões rápidas de caminho RTSP.
4. A lista principal mostra cartões com ações rápidas para abrir, copiar o endereço RTSP completo e editar/excluir a câmera.
5. Se a descoberta falhar, verifique as portas da câmera, Wi-Fi e ONVIF, ou use `rtsp://usuario:senha@IP:554/caminho`.


### Indicadores de bateria no vídeo (1.4.18)

No modo **Transmitir**, o app consulta a bateria do próprio celular transmissor via `ACTION_BATTERY_CHANGED`, no máximo a cada 15 segundos. Sobrepõe **percentual de carga** e **temperatura da bateria**, em °C, em um selo discreto no canto superior do vídeo. O selo é incorporado ao NV21 **antes da compressão MJPEG e do codificador MP4**, aparecendo tanto no celular receptor quanto nas gravações em `Downloads/MinhaCamera`. A prévia local de câmera única usa um selo visual equivalente; a prévia dupla já recebe a imagem codificada com o selo. **Mais → Ocultar/Mostrar bateria no vídeo** ativa/desativa a função, lembrando a preferência. Desativado, não adiciona o selo aos novos quadros.

A temperatura exibida **não é de CPU nem do ambiente**. Leituras ausentes/inválidas aparecem como `--` (nunca como medições inventadas); sensores podem atrasar ou não reportar temperatura. O recurso não usa IA, internet nem novas permissões. O selo usa um bitmap pequeno em cache e aplica pixels diretamente no NV21, evitando uma segunda compressão JPEG por quadro. Como as informações são visíveis a qualquer receptor autorizado e às gravações, o recurso pode ser desligado no menu Mais. Compilação Android e validação no aparelho ainda pendentes.

### Interface e celular da 1.4.17

No transmissor, o visual segue **Virar · Dupla · QR · Gravar/Parar · Mais**, com cinco cartões iguais na mesma linha. **Mais** reúne Link (copiar/enviar), Código (renovar acesso) e destino de gravação. **QR** continua em diálogo centralizado sob demanda. No modo Dupla, se o Android 11+ anunciar uma combinação frontal/traseira de câmeras concorrentes, Camera2 tenta capturá-las e compõe **traseira em cima/frontal embaixo**; em falha de hardware retorna à câmera única. **Não disponível em todos os aparelhos.**

A transmissão MJPEG usa até ~11 FPS em lugar de 4 FPS, dependendo de CPU/Wi-Fi, e o receptor substitui quadros aguardando a interface por quadros recentes. Não é garantia de latência específica. **Gravar** envia quadros NV21 a um codificador H.264/MP4 independente da rede, finaliza ao tocar Parar ou sair, e publica em **Downloads/MinhaCamera**, sem áudio. Android 7–9 pede autorização de armazenamento; Android 10+ usa MediaStore Downloads. O vídeo só aparece após finalização; interrupção abrupta pode perder a sessão em andamento. Codificadores de alguns aparelhos podem não suportar resolução/composição pedida, sobretudo no modo Dupla: o app mostra uma falha, não salva arquivo falso. **Build Android e validação física pendentes.**

### Interface da 1.4.14

A tela principal possui duas abas inferiores: **Câmeras** (câmeras RTSP/ONVIF persistidas, Buscar e Adicionar manualmente) e **Celular** (cartões **Transmitir** e **Assistir**, conexões de celulares já salvas). No transmissor, a prévia local fica em destaque; desde 1.4.17 os botões são **Virar, Dupla, QR, Gravar/Parar, Mais**. O QR é gerado apenas ao abrir o pop-up centralizado e não interrompe a câmera ao fechar. **Link** reúne Copiar e Enviar endereço; **Código** revoga o token após confirmação. Em Assistir, a captura QR é incorporada a um pop-up vertical na própria Activity, com opção manual. A URL privada não fica exposta na tela principal. O servidor MJPEG, cadastro RTSP e assinaturas permanecem os mesmos. Teste físico pendente.

## Modo Eco e controle de temperatura (1.4.20)

No transmissor, **Mais → Ativar Eco** reduz o teto de envio MJPEG de ~11 para ~5 FPS e a qualidade JPEG de 62 para 50. Essa alteração é imediata, não refaz QR, não reinicia o servidor e não interrompe a gravação. Eco é uma preferência local persistida e **não** desliga fisicamente nenhuma câmera nem impede seu funcionamento em aparelhos fracos; a câmera dupla permanece experimental.

A leitura de temperatura **da bateria** já existia: agora, quando ela atinge 42°C, aparece um alerta discreto. Em 45°C ou mais, o app reduz o teto para ~3 FPS e o JPEG para 45, mesmo sem Eco. Histerese reduz a oscilação: sai do nível alto abaixo de 42°C e encerra o alerta abaixo de 40°C. O app não mede temperatura da CPU nem garante prevenção de superaquecimento; o usuário deve observar aquecimento real, sistema Android e ventilação. Se faltar leitura, não inventa temperatura. A gravação em MP4 pode ter FPS reduzido; o contêiner/codec permanecem H.264 quando disponível.

Nenhuma biblioteca ou permissão nova; transmissão continua MJPEG local. Build e medições físicas pendentes.

### Modo Retrovisor do receptor (1.4.24)

Em **Celular → Assistir**, o receptor permanece em orientação vertical e mostra cinco controles compactos: **Refazer · Inteira/Preencher · Espelho/Normal · Foco · Mais**. **Espelho** faz apenas uma transformação de exibição (`ImageView.scaleX=-1`) no celular receptor. Nenhum quadro é recodificado, nenhuma alteração é enviada ao transmissor, e o ajuste fica salvo no aparelho receptor. O padrão é imagem normal, sem espelhamento. Importante: textos já gravados nos pixels do vídeo são invertidos junto com a imagem; a telemetria nativa sobreposta à prévia permanece legível.

**Foco** aumenta a área da imagem e esconde elementos secundários; toque no vídeo para voltar. Ao perder o sinal, o receptor limpa o último quadro em vez de deixar uma imagem congelada aparentando ser ao vivo. Essa tela foi pensada para uso como retrovisor, mas **não substitui espelhos físicos nem elimina pontos cegos**. Nenhuma nova dependência ou permissão. Build Android e ensaio em dois celulares ainda pendentes.

## GitHub Actions

O workflow `.github/workflows/build-apk.yml` roda em push nas branches `main`/`master` ou manualmente, prepara Android SDK 36 e executa `gradle testProtocol testReleaseUnitTest assembleRelease` uma única vez, com cache. Publica cada APK individualmente, sem ZIP, em **Actions > Artifacts**:

- `MinhaCamera-1.4.27-universal.apk`: todas as quatro ABIs, para distribuição geral.
- `MinhaCamera-1.4.27-armeabi-v7a.apk`: Android ARM de 32 bits.
- `MinhaCamera-1.4.27-arm64-v8a.apk`: Android ARM de 64 bits.
- `MinhaCamera-1.4.27-x86_64.apk`: Android Intel de 64 bits/emuladores.
- `MinhaCamera-1.4.27-x86.apk`: Android Intel de 32 bits/emuladores legados; mantido para preservar suporte anterior.

No workflow assinado, todos têm `com.minhacamera`, versão 1.4.27, código 37 e a mesma assinatura dentro do build. A validação local sem chave gera somente APKs não assinados, descritos em PLAYER_FIT_VALIDATION.md. Escolha pela ABI do **sistema Android**, não só pela CPU. O relatório `apk-report.md` e `SHA256SUMS` acompanham os artefatos. O verificador compara também os bytes das bibliotecas com o Universal, sem recompilar o app por ABI.

A 1.4.16 corrige o bloqueio do Build APK 18 causado por KEYSTORE_BASE64 inválido. O workflow prefere o novo Secret KEYSTORE_HEX, composto apenas de dígitos e letras a–f, mantendo Base64 como alternativa legada. É obrigatório importar KEYSTORE_HEX e conferir os outros três Secrets antes do novo build. O certificado original é mantido; nenhuma chave foi regenerada.

A partir da 1.4.13, o Release **exige uma keystore PKCS12 permanente e os Secrets necessários**, e o verificador confere o SHA-256 do certificado com `docs/SIGNING_CERT_SHA256.txt`. Sem os Secrets, o workflow falha antes da compilação. Chaves privadas ficam somente no arquivo de backup e nos Secrets do repositório; não são commitadas. **Se o APK instalado foi assinado com outra chave, não é possível atualizar sem recuperar a assinatura original**. Preserve dados antes de qualquer desinstalação. Consulte [configuração detalhada](docs/ASSINATURA_PERMANENTE.md).
O cache de tarefas é habilitado com `org.gradle.caching=true`; o `gradle/actions/setup-gradle@v6` restaura/salva o Gradle User Home após um job concluído. A execução não deve ser cancelada assim que aparecer `BUILD SUCCESSFUL`, porque ainda faltam nomear/publicar o APK e finalizar o cache. O primeiro build pode continuar demorado, e mudanças no projeto invalidam parte do cache. A mensagem `BUILD SUCCESSFUL in 2m 8s` do build #9 foi seguida de cancelamento externo, antes de publicar o APK.

Para atualizar, siga [docs/VERSIONING.md](docs/VERSIONING.md), execute `python3 scripts/check_version.py`, `python3 scripts/check_build_config.py` e, onde houver `kotlinc`, `bash scripts/test_protocol.sh`; depois sincronize versões, documentos e histórico e confirme o build no GitHub.

## Limitações e segurança

- O sucesso da descoberta não garante que o dispositivo exponha um stream RTSP suportado.
- ONVIF pode anunciar IP/porta diferentes dos esperados; o app usa o IP do dispositivo que respondeu ao probe.
- Senhas em URLs RTSP ainda são armazenadas em preferências locais sem criptografia. O backup Android foi desabilitado em 1.4.2 como mitigação; a proteção com criptografia continua pendente. Evite compartilhar exportações/relatórios que possam conter URLs.
- LibVLC adiciona bibliotecas nativas e aumenta o download/armazenamento; sua distribuição está sujeita aos termos aplicáveis da licença LGPL (ver [VideoLAN](https://www.videolan.org/legal.html)).
- Para testar H.265 real é necessário compilar e executar no Android. Os testes simulados não são garantia de compatibilidade.

Pacote: `com.minhacamera` · minSdk 24 · targetSdk 34 · compileSdk 36 · AGP 8.11.1 · Gradle 8.13 · Kotlin 2.2.20 · Media3 1.3.1 · LibVLC 3.7.7.

## Gravação e vídeo com duas lentes

No player: **Gravar** → **Iniciar gravação**. Se necessário, o app conecta o mesmo URL atual pelo LibVLC, espera a primeira imagem e pede gravação nativa; não abre uma segunda conexão em paralelo nem transcodifica. **Parar** finaliza o arquivo. Um contador REC aparece somente após confirmação nativa. O app verifica espaço livre durante a sessão e encerra a gravação quando o player é interrompido ou a tela fica em segundo plano. Não é gravação 24h/serviço de fundo.

**Gravações** reúne vídeos deste celular por dia, com abrir em player externo, compartilhar, salvar cópia e excluir. Os originais ficam na pasta privada do app, sem permissões de armazenamento abrangentes. **Salvar cópia** usa o seletor Android e preserva o arquivo fora do app; desinstalar remove os originais privados. Não busca dias antigos do cartão SD/NVR da câmera.

**Imagem** oferece imagem inteira, lente superior inteira e lente inferior inteira, sempre sem zoom para câmeras que juntam duas lentes em um único fluxo. Somente a outra metade é ocultada; a lente escolhida mantém todas as bordas e a proporção, com barras quando necessário. A gravação salva o fluxo completo. **Tela cheia** esconde a barra superior e barras do sistema. Não há controle PTZ nem seleção física de lentes implementados.

Resultados atuais em [PLAYER_FIT_VALIDATION.md](docs/PLAYER_FIT_VALIDATION.md). Histórico da 1.4.8 em [UI_RECORDING_VALIDATION.md](docs/UI_RECORDING_VALIDATION.md). O comprovante anterior [BUILD_VALIDATION.md](docs/BUILD_VALIDATION.md) se refere somente à 1.4.7.

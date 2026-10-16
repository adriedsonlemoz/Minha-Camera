## 1.4.53+63 — 2026-10-16
- Evidência da Jortan JT-9686 no relatório 1.4.52: caminho `/` devolveu DESCRIBE 200 autenticado com SDP H.265, mas LibVLC exibiu zero bytes e zero quadros nas duas rotas RTSP; a falha de reprodução não está confirmada como problema de codec.
- LibVLC tenta URL RTSP com login (comportamento anterior) e, em caso de falha, repete a mesma combinação de caminho/transporte com URL sem user-info e credenciais entregues em opções `rtsp-user` e `rtsp-pwd`. Não modifica URLs nem senhas gravadas.
- Diagnóstico avança além de DESCRIBE 200: avalia `a=control` do vídeo, SETUP para RTP/TCP intercalado, PLAY e presença de bytes intercalados após o PLAY. Diferencia o retorno 461 (transporte TCP não suportado) de outras falhas, sem exportar nonce, Session-ID ou senha.
- Sondagem SETUP/PLAY limitada à rede local, mesma origem do serviço RTSP, com fechamento do socket após verificação. Não substitui testes reais do VLC/decodificador nem garante reprodução H.265.
- Testes simulados com `401 Digest → DESCRIBE 200 → SETUP 200 → PLAY 200 → RTP` e variante SETUP 461, além de regressão de interface/versão e integridade do projeto. Build Android e testes físicos continuam pendentes.

## 1.4.52+62 — 2026-10-16
- Aceita URLs diretas RTSP, HTTP(S)/MJPEG, HLS (.m3u8), MPEG-DASH (.mpd), RTMP/RTMPS e RTP/UDP experimental, via LibVLC. Fluxos que não sejam RTSP não recebem tentativas RTSP.
- Cadastro manual/edição permite URLs diretas para várias marcas sem perder câmeras salvas. As credenciais dos campos separados são adicionadas à URL RTSP se ainda não houver login embutido. Apenas URL HTTP com token válido MCRP é tratada como celular.
- Varredura acrescenta portas TCP 80, 8080 e 5000 para identificar serviços HTTP e ONVIF, mantendo descoberta ONVIF/WS-Discovery.
- Nova explicação dos métodos disponíveis no menu do player. Não promete WebRTC ou P2P proprietário sem SDK/sinalização.
- Diagnóstico exige faixa de vídeo no SDP para confirmar DESCRIBE 200; inclui orientação para conferir senha NVR/RTSP diante de autenticação recusada.
- Verificações locais e simuladas aprovadas; a compatibilidade final de cada formato depende do VLC compilado, da câmera e de testes no APK.

## 1.4.51+61 — 2026-10-16
- Evidência da câmera 209: OPTIONS 200 confirma um servidor RTSP; DESCRIBE retorna desafio de autenticação sem conclusão; VLC não recebe quadros.
- RtspProbe agora tenta reaproveitar desafio Digest após fechamento do socket (reconexão no mesmo host) e considera URI-base nos firmwares legados, sem inventar senhas ou alterar endereços.
- LibVLC recebe opções `rtsp-user` e `rtsp-pwd` a partir das credenciais decodificadas que já estavam salvas na URL. Nenhum segredo é incluído no relatório.
- Diagnóstico diferencia credenciais presentes, método anunciado e códigos RTSP; não confunde OPTIONS 200 com vídeo acessível.
- Teste Kotlin/JVM simula servidor que fecha após 401 e só aceita a tentativa autenticada na segunda conexão.
- Build Android e teste com a câmera física continuam pendentes.

## 1.4.50+60 — 2026-10-16
- Novo teste de regressão RTSP: rota padrão do Android como comportamento inicial, com troca manual opcional para Wi-Fi vinculado no menu.
- Ação "Testar no VLC externo" solicita confirmação antes de enviar a URL RTSP (inclusive credenciais, quando presentes) ao app independente VLC.
- Diagnóstico expandido com dados de sessão do LibVLC, estatísticas de quadros e bytes, tentativa de transporte e resposta a OPTIONS separada do DESCRIBE.
- Nenhuma alteração automática de URL/senha das câmeras cadastradas; mantém o Media3 RTSP desativado por causa da falha histórica do parser CSeq.
- Falta compilar no Android/CI e confirmar em hardware real o resultado da Câmera 209.

## 1.4.49+59 — 2026-10-16
- Tela Novidades centralizada verticalmente quando há espaço, com rolagem preservada para telas pequenas e fontes ampliadas.
- Pop-up de diagnóstico: Fechar neutro, Copiar azul sólido e Exportar com contorno azul, mantendo acessibilidade e ações existentes.
- Diagnóstico RTSP assíncrono atualiza uma janela aberta quando `DESCRIBE` terminar; o TXT passa a refletir o resultado efetivo sem duplicar a lista de tentativas.
- Arquivo de diagnóstico da Câmera 209 analisado: `/onvif1` e `/onvif2` em TCP e UDP sem quadros; não há evidência suficiente para atribuir a causa ao codec ou autenticação.
- Compilação Android e teste físico ainda pendentes.

## 1.4.48+58 — 2026-10-16
- Diagnóstico RTSP com botão discreto no topo e relatório local em modal minimalista com Fechar, Copiar e Exportar TXT (Android SAF).
- Relatórios de informação de transmissão e identificação ONVIF/serviços em janelas próprias, sem pressupor marca ou capacidades que não responderam.
- Detecção diferencia resposta RTSP, HTTP, outro protocolo e porta TCP silenciosa; lista sinaliza quando RTSP NÃO foi confirmado.
- Em caso de falha, permite recuperar o fluxo em portas/caminhos RTSP conhecidos da rede privada sem substituir automaticamente a configuração. Só salva o novo endereço após confirmação do usuário e primeiro quadro real.
- Boas-vindas redesenhadas com apenas 3 destaques desta versão, sem juntar histórico longo nem conteúdo antigo.
- Mantidos MJPEG/H.264 entre celulares, gravação opcional, orientação e modo retrovisor. Compilação APK/testes físicos pendentes.

## 1.4.47+57 — 2026-10-16
- Visualizador: orientação automática (Seguir rotação do Android) entra em tela cheia em paisagem, com apenas cinco ações num painel semitransparente sobre o vídeo. Em retrato restaura o painel completo de 19 cards. Sem reiniciar a conexão ou a gravação.
- Mantém saída manual da tela cheia no horizontal; rotação fixa vertical/horizontal não aciona esta passagem automática. Preservados foto, gravação, PiP e o botão Girar para ajuste manual.
- Transmissor: corrigido sinal da rotação dos bytes NV21 frente ao `OrientationEventListener`: câmera traseira soma ângulo horário, frontal subtrai. Convertido `Display.rotation` anti-horário para o referencial do sensor no início da captura tanto na Activity como no serviço de tela apagada.
- Verificação Kotlin/JVM dos quatro ângulos e ambas as câmeras; testes estáticos da transição de tela cheia. APK Android e teste real ainda pendentes.

## 1.4.46+56 — 2026-10-16
- Receptor de câmeras RTSP passa a testar LibVLC com TCP e UDP para cada URL principal/alternativa antes de concluir falha de reprodução.
- Player conecta explicitamente pela rede Wi-Fi local quando disponível, útil para câmeras sem Internet.
- Após esgotar os fluxos, roda diagnóstico RTSP `DESCRIBE` sem expor URLs/senhas: alcance da porta, autenticação, caminho e SDP/codec informados pelo servidor.
- Botão **Copiar diagnóstico RTSP** na tela de erro e política de fallback testável em Kotlin/JVM.
- Mantido o bloqueio de Media3 RTSP e preservadas gravação, PiP, orientação e outras telas.
- Build Android e validação em câmera física permanecem pendentes.

## 1.4.45+55 — 2026-10-16
- Corrigida a falha do Build APK 45 em `:app:compileReleaseKotlin`: quebras de linha reais dentro de strings de relatório em `MainActivity.kt` (linhas 483–519).
- O relatório **Analisar e aplicar** volta a usar sequências escapadas `\n` / `\n\n`; mantidas as funcionalidades RTSP e orientação de câmera.
- Adicionado preflight `scripts/check_kotlin_strings.py` com quatro testes de regressão, executado pelo GitHub Actions antes da compilação.
- Parser PSI Kotlin, validações estáticas e testes locais aprovados; compilação Android/CI e teste em dispositivo permanecem pendentes.

## 1.4.44+54 — 2026-10-15
- Corrigida a orientação do transmissor nos modos **Vertical fixo** e **Horizontal fixo** para evitar imagem de cabeça para baixo quando o aparelho está no lado oposto da rotação.
- Os modos fixos agora preservam a família retrato/paisagem, mas escolhem automaticamente o lado físico correto (0/180 ou 90/270) com base na posição real do aparelho.
- O sensor de orientação permanece ativo também nos modos fixos, tanto na Activity quanto no serviço em segundo plano, apenas para decidir o lado correto sem reintroduzir oscilações rápidas.
- Build Android/APK e teste em aparelho ainda pendentes nesta entrega.

## 1.4.43+53 — 2026-10-15
- Tela **Usar o celular como câmera** reorganizada com controles e ajustes visíveis em grade na própria página, sem depender do menu Mais para funções principais.
- Novos cards diretos para Link, Código, sobreposição de bateria, Eco, tela apagada, orientação, resolução, FPS, codec, perfil MP4 e diagnóstico, preservando QR, gravação, troca de câmera e modo duplo.
- Câmeras RTSP já cadastradas agora oferecem **Analisar e aplicar** no menu de opções (toque longo), para reexaminar o fluxo e atualizar automaticamente URL principal e alternativas salvas.
- Validação Android/CI e teste físico do APK ainda pendentes nesta entrega.

## 1.4.42+52 — 2026-10-15

- Corrige o Build APK 42: o AAPT2 interrompia `:app:processReleaseResources` com 16 erros `attribute insetLeft/Right/Top/Bottom not found` em `item_camera.xml`.
- Retirados apenas os atributos `app:inset*` inválidos dos quatro `MaterialButton`; mantém os quatro controles na mesma linha e seus identificadores, ações, estilos e largura por peso.
- O preflight `scripts/check_android_resources.py` agora também valida layouts contra esses atributos incompatíveis; teste de regressão cobre o caso.
- Preservados orientação automática/vertical/horizontal, transmissão, gravação, PiP e Secrets de assinatura. **Sem compilação Android completa nem teste em dispositivo nesta entrega; executar o workflow para verificar.**

## 1.4.41+51 — 2026-10-15

- Central Multimídia passa a atalho azul ao lado de Buscar, Adicionar e Manual; quatro ações por câmera na mesma linha, com rótulos legíveis e sem revelar credenciais do RTSP.
- O visualizador exibe 19 ações e configurações em cards compactos: cinco colunas quando couber, quatro ou três nas telas mais estreitas, preservando rolagem vertical e acessibilidade.
- Itens antes no diálogo Mais passam a ser visíveis: orientação, bateria no vídeo, indicadores, biblioteca remota, detalhes técnicos, formato e diagnóstico; resolução/FPS/câmera/Eco mostram estados abreviados nos próprios cards.
- Sem alterações de protocolo, gravação ou permissões. Verificações estáticas disponíveis; compilação Android e teste visual/em dispositivos ainda pendentes.

## 1.4.40+50 — 2026-10-15

- Captura MJPEG e H.264 com orientação automática estável pelo sensor (800ms) ou modos manualmente fixados em vertical/horizontal. Opção na tela Transmitir → Mais → Orientação da câmera.
- Orientação atualizada também pelo serviço da câmera com a tela apagada quando o sensor fornece eventos; preferência manual mantém ângulo previsível em aparelho fixo.
- Na gravação MP4, a geometria fica congelada e eventuais mudanças são aplicadas ao finalizar REC, sem alterar dimensões no meio do arquivo.
- Visualizador permite Seguir rotação do Android, Vertical fixo ou Horizontal fixo via Mais, com proporção de imagem preservada, inclusive PiP. Status de resolução informa dimensões reais após rotação aos consumidores MCRP.
- App continua independente do Vigia IA; H.264 experimental pode exigir reconexão após giro. Build APK e validação em celulares ainda pendentes.

## 1.4.39+49 — 2026-10-15

- Segunda passada de design na tela inicial, com herói premium e ações principais em destaque.
- Biblioteca do outro celular reformulada, com cartões de arquivos e ações diretas para abrir e baixar.
- Tela de transmissão refinada com painel de status mais moderno e acabamento visual melhor.
- Alteração focada em interface; build APK e testes em aparelho continuam pendentes.

## 1.4.36+46 — 2026-10-15

- Protocolo MCRP v1 versionado e retrocompatível com HTTP/QR existentes.
- Servidor de biblioteca remota autenticado: MP4/fotos do Minha Câmera, cabeçalhos Range e retorno 206 para reprodução parcial, sem excluir arquivos.
- Central Multimídia e visualizador com acesso à biblioteca do outro celular para consultar, reproduzir e baixar cópias.
- Cliente Vigia IA 1.0.244 com leitura QR, MJPEG/H.264, estado, comandos e arquivos (em projeto separado).
- Testes Kotlin/JVM de autenticação, listagem, download e HTTP Range. Compilação Android e aparelho pendentes.

## 1.4.35+45 — 2026-10-15
- Corrigido Build APK 35: remoção do import `android.os.SystemClock` duplicado em `PhoneHeadlessPreview.kt`, que causava `Conflicting import` nas linhas 11/13 durante `:app:compileReleaseKotlin`.
- Adicionado `scripts/check_kotlin_imports.py` ao preflight do GitHub Actions para sinalizar imports Kotlin duplicados ou nomes de imports ambíguos antes do build.
- Incluídos testes de regressão de imports sem alterar transmissão MJPEG/H.264, câmera com tela apagada, UI, gravação ou assinatura.
- Compilação Android completa e teste em dispositivo dependem do próximo workflow do GitHub Actions.

## 1.4.34+44 — 2026-10-15
- Corrigido Build APK 34 (`:compileProtocolTests`): incluído `PhoneAvcAnnexB.kt` nas fontes do compilador Kotlin/JVM configurado em `build.gradle.kts`.
- Adicionado verificador `scripts/check_protocol_sources.py`, executado no preflight do GitHub Actions, para impedir divergência entre as fontes de teste do Gradle e de `scripts/test_protocol.sh`.
- Mantidas as funções H.264/MJPEG, nova tela inicial, controles remotos, gravação e assinatura permanente sem alterações de comportamento.
- Compilação do APK da 1.4.34 ainda depende de novo workflow no GitHub Actions.

## 1.4.33+43 — 2026-10-15
- Tela inicial reformulada: câmeras IP RTSP e celulares HTTP na mesma lista, ações Abrir/Copiar/Editar/Excluir visíveis e atalhos Transmitir/Assistir no topo.
- Central Multimídia com gravações RTSP e seleção de fotos/vídeos do dispositivo.
- Engrenagem Configurações, temas claro/escuro/sistema/AMOLED, Sobre com três últimas versões e apoio voluntário por Pix; apoio pelo Google apresentado sem cobrança até configuração do Billing.
- Eco/REC/troca de câmera/foto nativa no servidor do transmissor com tela ligada; status de modelo/resolução para identificar aparelhos remotos.
- Respostas de controle diferenciam bloqueio por gravação MP4 de funções indisponíveis; o botão Refazer foi renomeado Reconectar.
- MJPEG/H.264, diagnósticos, modo de tela apagada e assinatura permanente preservados.

## 1.4.32+42 — 2026-10-15
- H.264/AVC **experimental** adicionado para transmissão pela rede local, com codificação MediaCodec e playback LibVLC da rota autenticada `/h264/TOKEN`. MJPEG permanece padrão e como fallback.
- Seletores MJPEG/H.264 no topo do transmissor e receptor, escolha remota autenticada, recuperação ao mudar de codec.
- Perfis locais e remotos 360p, 480p, 720p, 1024×768 e 1080p conforme câmera; FPS-alvo 5, 10, 15, 24 ou 30. Gravação MP4 configura FPS e bitrate, sem prometer FPS real ou resolução independente do preview.
- Cronômetro AO VIVO independente de REC nos dois aparelhos, incluindo tela apagada.
- Botões de diagnóstico locais e remoto. Logs limitados e privados de travamentos, perdas de quadros, EGL, codificação e reinícios do watchdog; exportação e cópia.
- Limitações: H.264 bruto HTTP/Annex-B e codec YUV420 requerem teste real, não há APK/compilação Gradle local. Redmi A3 pode limitar encoder, resolução, FPS e duração no background.

## 1.4.31+41 — 2026-10-15
- Corrigidos os quatro erros Kotlin do workflow Build APK 31 (`:app:compileReleaseKotlin`).
- Callbacks de qualidade e troca remota passam a resolver o servidor ativo após sua inicialização, sem capturar `session` no próprio construtor.
- Seletores remotos de qualidade/FPS agora tratam valores `Int?` não informados sem causar erro de tipos.
- Não há mudança de protocolo, permissões ou assinatura; recursos da 1.4.30 preservados.

## 1.4.30+40 — 2026-10-15
- Transmissor passou a oferecer prévia sem transmissão e botão Iniciar/Parar transmissão, distinto de REC.
- Serviço com tela apagada ganhou consumidor OpenGL da SurfaceTexture, corrigindo provável bloqueio da fila de buffers do preview.
- Rotas autenticadas para foto JPEG nativa máxima, perfis de resolução (360/480/720p), FPS (5/10/15) e troca frontal/traseira no serviço persistente.
- Recepção do tempo de gravação em headers MJPEG; tela cheia com controles compactos e ícones visíveis para PiP/Inteira/Expandir.
- Boas-vindas/Novidades uma vez após instalar/atualizar para um novo versionCode.
- Compatibilidade e efeitos térmicos ainda dependem de build e teste físico no Redmi A3.

## 1.4.29+39 — 2026-10-15
- Visualizador MJPEG renovado: cabeçalho da câmera real, bateria compacta, card de vídeo, temperatura, resolução real e FPS efetivo.
- Comandos funcionais: REC remoto, foto do frame salva no receptor, giro local 180°, expandir, PiP, inteira, espelho e refazer.
- Painel com Eco remoto autenticado quando o transmissor opera em foreground service; indicadores técnicos somente sob demanda.
- Menu Mais redesenhado com ícones e switches. Mantidos PiP, reconexão, RTSP LibVLC e demais módulos.
- Zoom, flash, troca remota de lentes e resolução configurável não estão habilitados; evitam-se controles sem backend.

## 1.4.28+38 — 2026-10-15
- Renovado o pop-up **Mais** do transmissor com layout personalizado, ícones e switches para opções ativáveis.
- Link, Código e Gravações receberam subtítulos e visual mais claro.
- Estados de bateria no vídeo, modo Eco e tela apagada agora aparecem imediatamente no diálogo.

## 1.4.27+37 — 2026-10-15

- Transmissão e gravação opcional com tela apagada: ForegroundService de câmera, superfície independente da Activity, notificação com Gravar/Parar e Encerrar, proteção de suspensão e trava Wi-Fi durante a captura. Habilitação explícita com tela visível e apenas uma câmera.
- No receptor MJPEG, Mais oferece gravação remota do transmissor. Comando HTTP POST usa o mesmo token secreto da URL MJPEG e só funciona no serviço persistente (iniciar/parar idempotente). Incluído teste de protocolo com token correto e inválido.
- Desacopla o vídeo e o MP4 dos indicadores de bateria: remove o selo NV21 permanente, evitando sobreposição duplicada no PiP e mantendo gravações limpas.
- Receptor permite selecionar bateria, temperatura e raio de carregamento individualmente, com preferências persistentes e indicador compacto sem média de corrente ou texto de ausência de carga.
- Câmeras RTSP continuam no LibVLC (contorno do crash Media3 da 1.4.26). Recursos PiP, modo espelho e gravação anterior preservados.
- Pendências: build Android assinado, Android 14/Redmi A3 com tela apagada, câmera MJPEG prolongada e gravação MP4 real não foram testados em dispositivo.

## 1.4.26+36 — 2026-10-15

- Corrigido o caminho de fechamento ao conectar RTSP: `NullPointerException` não tratada em `RtspClient.MessageListener.handleRtspResponse` do Media3 1.3.1 (linha 544; leitura de cabeçalho `CSeq` ausente).
- LibVLC 3.7.7 agora é o único motor RTSP acessível por iniciar, repetir conexão e trocar URLs alternativas. A opção Media3 foi suspensa no menu, e há uma proteção central para impedir seu acionamento indireto.
- Não há mais tentativa inicial com Media3 antes de cair para LibVLC: a exceção era lançada na thread interna, antes de um fallback. O Media3 permanece como código desativado para reavaliação futura após atualização/testes.
- Mantidos PiP nativo RTSP/MJPEG, gravação por LibVLC, espelhamento do receptor, descoberta ONVIF, cinco ABIs e assinatura existente. Nenhum dado cadastrado é apagado.
- Análise baseada no log entregue; sem captura do tráfego bruto não é possível provar se a câmera omitiu `CSeq` ou se o cabeçalho se perdeu na resposta. Compilação/funcionamento no Android ainda pendentes.

## 1.4.25+35 — 2026-10-15

- PiP nativo em **Celular → Assistir** (MJPEG), com vídeo isolado, sem controle extra ou sobreposição por permissão especial. A leitura sobrevive ao `onPause` e termina no `onStop` quando PiP não estiver ativo; reconexão e espelho preservados.
- PiP nativo no player RTSP (botão PiP e menu) após primeira imagem; controles escondidos na janela, sem iniciar durante gravação para evitar cortes.
- Falha reportada em vídeo: abertura da câmera RTSP “Câmera 209” retorna imediatamente à tela inicial. O vídeo não contém Logcat, então a causa exata permanece desconhecida.
- Media3 captura `LinkageError` de inicialização para permitir fallback LibVLC em vez de término por erro não tratado, e mostra melhor resumo de falha sem revelar URL.
- `CameraApplication` grava diagnóstico *Java/Kotlin* mínimo no armazenamento privado em crash e o oferece para cópia na próxima abertura do app (sem URL, senha ou stacktrace completo com parâmetros). Crashes nativos não são capturados.
- Assinatura Release, Secrets, MJPEG, RTSP e gravação preservados. Faltam build completo Android e testes físicos com Logcat/firmware.

## 1.4.24+34 — 2026-10-15
- Receptor em modo retrato com cinco ações compactas: Refazer, Enquadrar, Espelho, Foco e Mais.
- Espelhamento horizontal opcional feito apenas por transformação de View, lembrado nas preferências do receptor. A câmera e o MJPEG não mudam.
- Modo Foco amplia o vídeo e esconde detalhes secundários; toque no vídeo para restaurar os controles.
- Em perda de sinal, limpa a imagem antiga para não confundir vídeo congelado com visão ao vivo.
- Telemetria na interface reduz atualizações redundantes. Sem novas dependências, permissões ou Secrets; gravação e câmera dupla preservadas.

## 1.4.23+33 — 2026-10-15
- Corrigido o erro AAPT2 `Invalid unicode escape sequence` em `novidades_text`, provocado por apóstrofo não escapado no histórico de novidades.
- Incluído verificador de recursos Android no pré-build do GitHub Actions e testes de regressão para caracteres e escapes problemáticos.
- Preservados câmera de celular/retrovisor, telemetria, RTSP/ONVIF, QR, gravação e assinatura estável.

## 1.4.22+32 — 2026-10-15
- Corrigido Build APK 23: `:compileProtocolTests` não encontrava `PhoneBatteryReading` após a telemetria do receptor.
- Modelo de telemetria extraído para `PhoneBatteryReading.kt` independente de Android; adicionado aos dois caminhos de compilação de protocolos (Gradle e script local).
- Regressão MJPEG ampliada para conferir cabeçalhos de bateria, temperatura, carregamento e média de corrente.
- Preservada a assinatura permanente e todas as telas e recursos da versão 1.4.21.

## 1.4.21+31 — 2026-10-15
- Receptor em layout retrato alinhado ao Ao vivo do transmissor, sem ícone/elemento indevido no centro da imagem.
- Telemetria ampliada do celular transmissor no receptor e na marca d'água: bateria, temperatura, status de carregamento e consumo médio do dia.
- Fluxo MJPEG agora envia cabeçalhos leves com esses dados; o receptor exibe overlay e detalhes próprios para uso como retrovisor.

## 1.4.20+30 — 2026-10-15
- Acrescentado **Modo Eco** persistente em Mais, sem dependências novas, reduzindo FPS máximo (~11→5) e qualidade JPEG (62→50) no MJPEG. A mudança não reinicia a câmera, o QR ou a gravação.
- Alerta térmico leve usando a temperatura da **bateria** já consultada de 15 em 15 segundos: aviso em 42°C e redução automática para ~3 FPS/JPEG 45 em 45°C, com histerese para estabilizar o aviso.
- Limitado também o ritmo da composição dupla e a atualização de sua prévia em Modo Eco. Monitor FPS permanece ativo durante gravação (correção de ciclo de vida).
- Sem mudanças em permissões, Secrets, keystore, arquitetura RTSP/ONVIF, QR ou assinatura. Build Android e testes em aparelhos reais pendentes.

## 1.4.19+29 — 2026-10-15

- Transmissor exibe FPS efetivo, com contagem atômica e atualização a cada 2 s; receptor mede FPS efetivamente mostrado na tela.
- Estimativa de atraso JPEG-publicado → quadro recebido usando timestamps monotônicos no multipart MJPEG e amostra de offset no handshake HTTP (não equivale à latência de captura/decodificação total).
- Reconexão do receptor após perda temporária do Wi-Fi, com backoff 1/2/4/8 s e cancelamento no ciclo de vida; erro de token não é repetido automaticamente.
- Mantidos MJPEG, gravação, câmera dupla experimental, interface de cinco ações, assinatura permanente e Secrets existentes; sem bibliotecas novas.
- Kotlin/API Android e testes físicos ainda pendentes de compilação integral no GitHub Actions e ensaio de aparelhos.

# Histórico de versões

## 1.4.18+28 — bateria e temperatura no vídeo ao vivo

- Adicionado selo discreto com **carga (%)** e **temperatura da bateria (°C)** do aparelho transmissor, incorporado em cada quadro MJPEG e também nos quadros usados para gravação MP4.
- Leituras pelo `ACTION_BATTERY_CHANGED` com cache de 15 segundos, validação de leituras ausentes e sem novas permissões.
- Prévia local possui indicação equivalente; opção **Mais → Ocultar/Mostrar bateria no vídeo**, persistida em preferências.
- Sobreposição aplicada diretamente nos planos Y/UV do NV21, evitando decodificação e recompressão extra, inclusive em câmera dupla.
- Mantidos QR sob demanda, câmera dupla experimental, gravação MP4 e assinatura estável. Build Android e testes de hardware ainda pendentes.

## 1.4.17+27 — transmissão mais fluida, gravação MP4 e câmera dupla experimental

- O modo Transmitir passa do limite de 4 FPS (250 ms) para uma tentativa de até ~11 FPS (90 ms), conforme a capacidade de CPU e rede. MJPEG não garante esse FPS nem latência fixa.
- O visual da tela Ao vivo segue os cinco controles do desenho aprovado: Virar · Dupla · QR · Gravar/Parar · Mais. O menu Mais preserva Link, renovação do Código e destino de vídeo.
- O receptor usa uma fila de apresentação de **um quadro recente**, descartando os pendentes antigos; servidor ajusta TCP_NODELAY e buffer de envio. O QR continua sob demanda em pop-up.
- Adicionada captura simultânea Camera2 **experimental** para pares frontal/traseiro explicitamente anunciados como concorrentes no Android 11+; duas imagens empilhadas. Quando o par não existe ou falha, utiliza câmera única.
- Implementado MP4 H.264 sem áudio no transmissor, com codificação em executor separado do MJPEG e transferência somente após finalização para `Downloads/MinhaCamera` via MediaStore (Android 10+) ou permissão legado (Android 7–9). Gravação termina ao sair da tela.
- Nenhuma alteração da assinatura release, packageName, biblioteca RTSP/ONVIF ou segredos. Performance real, gravação MP4 e compatibilidade Camera2 requerem testes físicos; o build completo Android não foi executado localmente.

## 1.4.16+26 — Build APK 18: assinatura por hexadecimal sem caracteres especiais

- Log Build APK 18: o `KEYSTORE_BASE64` do GitHub contém caracteres inválidos; o erro ocorreu **antes** do Gradle, embora as verificações de configuração e versionamento passassem.
- Adicionado `KEYSTORE_HEX` (hexadecimal ASCII 0–9/a–f) com **precedência** sobre o Base64 legado. O valor antigo pode permanecer no repositório sem interromper o build quando o novo Secret válido estiver configurado.
- O preparador nunca muda ou deduz bytes da chave: valida sintaxe/tamanho, abre o PKCS12 usando os Secrets e compara o certificado SHA-256 fixado.
- Novo importador privado para GitHub Manager, com `KEYSTORE_HEX`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` e `KEY_PASSWORD`; sem chave no repositório.
- Código dos protocolos, QR e interface da 1.4.14 preservado. Testes de integração Python passaram, mas a suíte Kotlin de protocolos excedeu o tempo de execução local. O build Android no GitHub Actions ainda precisa ser validado após importar os Secrets.

## 1.4.15+25 — 2026-10-14

- Corrigido pré-processamento da assinatura após o Build APK 17 falhar com `base64: invalid input` na v1.4.13+23. O erro ocorreu antes de qualquer compilação.
- Novo `scripts/prepare_signing.py`: aceita espaços, quebras de linha, aspas externas e padding Base64 ausente; rejeita conteúdo inválido e verifica o certificado SHA-256 antes de disponibilizar a keystore ao Gradle.
- Mensagens claras para reimportar Secrets corrompidos, sem mostrar valores ou passwords; `keytool` usa senha por variável de ambiente.
- Adicionados testes de regressão Base64 à etapa pré-build; preservadas a chave PKCS12, a assinatura e a interface 1.4.14.
- A recompilação Android e a correção do valor armazenado no Secret no repositório ainda precisam ser validadas no GitHub Actions.

## 1.4.14+24 — 2026-10-14

- Nova navegação inferior Câmeras/Celular, com listas separadas, ícones vetoriais e cartões personalizados.
- Cartões Transmitir/Assistir no menu Celular; câmera IP e cadastro manual preservados.
- Prévia da câmera com ações compactas Virar/QR/Link/Código.
- QR criado sob demanda em diálogo centralizado; sem endereço secreto no corpo da tela.
- Leitor QR via BarcodeView incorporado em diálogo de leitura retrato, sem Activity de captura em paisagem.
- Manutenção do token, assinatura Release estável e versionamento sequencial.
- Sem compilação Android ou ensaio em celular nesta rodada.

## 1.4.13+23 — 2026-10-14
- Assinatura Release permanente por quatro Secrets do GitHub Manager / GitHub Actions, usando keystore PKCS12 específica deste projeto.
- GitHub Actions valida a presença dos Secrets e o SHA-256 do certificado antes de compilar; falha de modo seguro se os dados estiverem incorretos.
- Gradle não usa mais a assinatura Debug em builds Release; o verificador de APKs confere o certificado fixado.
- Keystore e arquivos de importação gerados separadamente do código-fonte, excluídos do repositório por `.gitignore`.
- Não garante atualização sobre APK anteriormente assinado com chave diferente; instalação legada exige chave original ou migração com backup.
- Mantidos câmera/QR, RTSP/ONVIF, gravação e cinco APKs por ABI; build Android e teste de atualização em aparelho pendentes.

## 1.4.12+22 — 2026-10-14
- Corrigida a implementação da prévia local que podia aparecer preta mesmo com o QR e servidor iniciados: `SurfaceView` dentro de `ScrollView` foi substituído por `TextureView` com ciclo de vida de `SurfaceTexture` (inclui pausa, retorno e troca de câmera).
- Prévia posicionada antes do QR Code, com ajuste de proporção sem achatamento durante rotação/redimensionamento.
- «Transmitindo pela rede local» só é exibido após publicação efetiva do primeiro JPEG; tempo de espera de 5 s distingue ausência de quadros e falha na renderização da prévia.
- Não alterados MJPEG/QR, permissões, token, RTSP/ONVIF, gravações nem configuração de assinatura.
- Validação estática e testes sintéticos locais; build Android completo e confirmação no celular que apresentou a falha permanecem pendentes.


## 1.4.11+21 — 2026-10-14
- Ação «Usar este celular como câmera» agora pergunta o papel do aparelho: «Transmitir» ou «Visualizar».
- Em «Transmitir», a URL com o token de acesso é convertida em QR Code visível, atualizado ao trocar o código, a câmera ou o IP de rede; a imagem QR desaparece quando a transmissão para.
- Em «Visualizar», câmera abre leitor local de QR Code integrado ZXing, aceita exclusivamente URLs válidas do modo MJPEG privado, salva apenas novo endereço e inicia o vídeo imediatamente. QR inválido não abre links externos.
- «Adicionar outro celular» manual, cópia e compartilhamento de URL permanecem como opções auxiliares.
- Sem áudio, IA, análise de imagem, nuvem ou alteração de RTSP/ONVIF, gravações e assinatura; dependência nova `com.journeyapps:zxing-android-embedded:4.3.0`.
- Build Android, assinatura e teste de pareamento com dois aparelhos físicos ainda pendentes.

## 1.4.10+20 — 2026-10-14
- Novo modo «Usar este celular como câmera»: prévia local, permissão CAMERA, alternância de lente e streaming MJPEG via HTTP/IPv4 Wi-Fi.
- Visualizador no segundo Android, cadastro de URL LAN com token persistente revogável e manutenção das câmeras RTSP/ONVIF anteriores.
- MJPEG sem áudio, IA ou análise de imagem; compressão de baixa taxa, limite de 2 receptores, sem transmissão em segundo plano.
- Proteção da prévia da área de transferência para URLs/senhas copiadas; sem migração de credenciais legadas nesta versão.
- Testes sintéticos de token, URL, rotação NV21 e transporte MJPEG. Testes em aparelhos e build completo ainda necessários.
- Pendências ordenadas em docs/PHONE_CAMERA_IMPLEMENTATION.md.

## 1.4.9+19 — 2026-10-11
- Corrigido layout de controles durante rotação sem recriar o player: uma linha compacta na horizontal, duas em retrato.
- Ícones próximos ao texto, barra menor em horizontal e remoção do zoom que cortava a imagem.
- Cada metade do vídeo empilhado cabe inteira pela proporção real informada pelo decoder, incluindo SAR/orientação; só a outra lente fica fora da área visível.
- Consulta manual ONVIF de fabricante/modelo/firmware e serviços PTZ, gravação, busca e replay anunciados. Não envia movimentos nem presume suporte pelo nome Yoosee.
- Novos testes de geometria e identificação ONVIF simulada. Limites e build em PLAYER_FIT_VALIDATION.md.

## 1.4.8+18 | 2026-10-11

- Novo tema azul, campos contornados e compactos, RTSP em dropdown editável nos três formulários.
- Player escuro com carregamento até imagem exibida, botões por orientação, tela cheia e recortes para duas lentes.
- Gravação local no player LibVLC ativo, confirmação nativa, contador, controle de espaço e encerramento ao sair.
- Biblioteca por dia, abrir/compartilhar/exportar pelo Android e excluir; sem permissão ampla de armazenamento.
- Mantidos fallback Media3/LibVLC, HEVC, ONVIF, RTSP, cadastro e APKs por ABI.
- Validação e limites documentados em docs/UI_RECORDING_VALIDATION.md.

## 1.4.7+17 | 2026-10-11

- ABI splits habilitados: Universal, armeabi-v7a, arm64-v8a, x86_64 e x86, preservando todas as arquiteturas do LibVLC 3.7.7.
- Uma execução Gradle para testes e assembleRelease; cache preservado e artefatos APK individuais sem ZIP.
- Validação do AAR resolvido, bibliotecas por ABI, integridade ZIP, alinhamento, manifesto, assinatura e tamanhos reais, com relatório e SHA-256.
- Mesmos applicationId, versionName e versionCode 17 em todos os APKs; sem offsets por ABI.
- Preservados minSdk 24, funcionalidades, Media3 e LibVLC/H.265; minificação e remoção de recursos desabilitadas.
- Assinatura debug preexistente mantida. Atualização sobre APKs antigos depende da disponibilidade da chave original; não foi inventada chave ou Secret.
- Build local aprovado em 2m26s, seis testes simulados aprovados e cinco APKs verificados. ARM64: 62,69 MB (−71,18%); ARMv7: 47,85 MB (−78,00%).
- Certificado local diverge do APK anterior 1.4.6; atualização legada exige a chave original. Publicação GitHub bloqueada por 403. Detalhes em docs/BUILD_VALIDATION.md.

## 1.4.6+16 | 2026-10-11

### Diagnóstico do build #9 e otimização do CI
- Confirmado no log: `BUILD SUCCESSFUL in 2m 8s`, seguido de `The operation was canceled`; o cancelamento foi posterior à compilação e anterior à publicação do APK.
- Medições do build #9: cerca de 13 s para setup/instalação do SDK, ~54 s para configuração do Gradle e ~75 s para executar tarefas/empacotar. Não há base para afirmar que o LibVLC isoladamente dobrou o tempo.
- Habilitado `org.gradle.caching=true` e `--build-cache` para reutilização de saídas de tarefas elegíveis em builds futuros.
- Atualizado `gradle/actions/setup-gradle@v6` para modernizar o cache do Gradle User Home, incluindo cache local de tarefas.
- Acrescentada medição de duração do Gradle no log do workflow.
- Preservadas as bibliotecas, o suporte LibVLC para H.265 e `compileSdk=36`, sem restringir ABIs.

### Limitações
- Ganho de tempo não medido neste ambiente; primeiro build e alterações que invalidam cache continuam podendo demorar.
- Não cancelar o workflow após `BUILD SUCCESSFUL`: a publicação do artefato e a persistência do cache acontecem depois.
- Novo build completo e teste Android real pendentes.

## 1.4.5+15 | 2026-10-11

### Corrigido
- Build GitHub Actions #8 falhou na instalação do Android SDK com `sdkmanager: command not found` (exit code 127); a compilação Gradle não foi iniciada.
- Incluída `android-actions/setup-android@v4` para instalar/configurar ferramentas de linha de comando do SDK, preparar o `PATH` e aceitar licenças antes da execução de `sdkmanager`.
- Instalação de `platforms;android-36` e `build-tools;35.0.0` passa a verificar a existência de `sdkmanager` e do `android.jar` da API 36, sem suprimir falhas.
- Ações do GitHub `checkout` e `setup-java` atualizadas para versões atuais com Node 24 (`v7` e `v6`, respectivamente), preservando JDK 17.
- Ampliado `scripts/check_build_config.py` para prevenir regressão de ordem das etapas ou uso de `sdkmanager` sem preparação.

### Mantido
- `compileSdk=36`, `targetSdk=34`, `minSdk=24`, AGP 8.11.1, Gradle 8.13, Kotlin 2.2.20 e LibVLC 3.7.7.
- A compilação APK e o teste de câmera real continuam pendentes da próxima execução do GitHub Actions.

## 1.4.4+14 | 2026-10-11

### Corrigido
- Falha confirmada no build GitHub Actions #7 (`:app:checkReleaseAarMetadata`): LibVLC 3.7.7 exige `compileSdk >= 36`, enquanto a versão anterior usava 34 e AGP 8.5.2.
- Atualizados `compileSdk` para 36, Android Gradle Plugin para 8.11.1, Gradle para 8.13 e Kotlin Gradle Plugin para 2.2.20, um conjunto compatível documentado pelos fornecedores.
- Workflow instala explicitamente `platforms;android-36` e `build-tools;35.0.0` antes da compilação.
- Corrigida uma string de regex malformada em `PlayerActivity.kt`, no mascaramento de credenciais RTSP, que poderia provocar novo erro na compilação Kotlin.
- Adicionado `scripts/check_build_config.py` e sua execução no GitHub Actions para prevenir regressões de versões do SDK/Gradle.

### Mantido
- `minSdk=24` e `targetSdk=34`: aumentar `compileSdk` não altera o comportamento-alvo do Android.
- LibVLC para fallback H.265, descoberta ONVIF e cadastro de câmeras existente.

### Validação
- Regressão de protocolos, validação do versionamento, verificação estática de configuração de build e XML.
- É necessário recompilar no GitHub Actions para confirmar o APK: sem Android SDK ou acesso às dependências neste ambiente.

## 1.4.3+13 | 2026-10-11

### Implementado
- Repaginada a lista de câmeras salvas com cartões e ações rápidas para abrir o vídeo, copiar o endereço RTSP e acessar mais opções.
- Nova ação para **copiar o endereço RTSP completo com usuário e senha**, reduzindo o tempo para reutilizar a câmera em outros apps e testes.
- Diálogo de configuração de câmera encontrada reformulado com campo de senha com ver/ocultar, botões para copiar/colar e sugestões rápidas de caminho RTSP.
- Adição manual e edição de câmeras agora usam formulário estruturado com nome, IP/URL, porta, usuário, senha e sugestões de caminho com opção **Default**.
- Edição de câmera salva deixa de depender apenas da URL crua e passa a permitir ajuste mais rápido dos dados principais.

### Validação estática
- Revisão dos layouts Android, novas strings e adaptação do fluxo de cadastro/edição/listagem.
- Sem compilação de APK neste ambiente; testes reais em Android permanecem pendentes.

## 1.4.2+12 | 2026-10-11

### Implementado
- Motor LibVLC 3.7.7 como fallback automático após falhas do Media3, inclusive quando há fluxos H.265/HEVC.
- Menu do player permite escolher manualmente Media3 ou LibVLC.
- Extração do codec de vídeo do SDP retornado por `RTSP DESCRIBE`.
- Prioriza substream H.264 somente quando detectado efetivamente na resposta SDP, mantendo outros caminhos como alternativas.
- Timeout de 15 segundos para inicialização no LibVLC, prevenção de callbacks duplicados, descarte de recursos nativos e ocultação de URLs RTSP nos detalhes de falha.
- Backup Android desativado devido ao armazenamento legado de credenciais em URLs.
- Testes simulados adicionais de SDP, DESCRIBE H.265 e ausência de falso positivo em áudio.

### Limitações
- Código-fonte sem APK compilado; integração LibVLC ainda depende de build e testes no dispositivo.
- H.265 não é garantido para toda câmera. LibVLC aumenta significativamente o tamanho do APK.
- Criptografia das credenciais em repouso ainda pendente.


## 1.4.1+11 | 2026-10-11

### Corrigido
- Descoberta automática inclui WS-Discovery (multicast UDP/3702) e usa `ProbeMatch/XAddrs` para consultar o serviço ONVIF real.
- A varredura TCP lê a máscara IPv4 do Wi-Fi, não presume `/24`, e testa 554, 8554, 10554, 8555 e 7447.
- Resultados ONVIF e TCP são consolidados por IP, e todas as portas abertas encontradas são preservadas e testadas.
- Paralelismo TCP reduzido de 64 para 20 tarefas; nova varredura cancela trabalho anterior e descarta respostas antigas.
- Tratamento RTSP não encerra todas as variantes de autenticação na primeira resposta 401.
- Respostas RTSP passam a respeitar `Content-Length` em bytes, inclusive corpos SDP UTF-8.
- Diagnóstico da busca identifica ONVIF e as portas TCP abertas; aviso de rede muito grande.
- Menu "Novidades e versões" adicionado à tela principal.

### Mantido como limitação
- Certos fluxos H.265 com ausência de `sprop-vps` continuam incompatíveis com o Media3 utilizado. A integração do LibVLC **não** foi realizada nesta versão.
- A descoberta depende da rede permitir multicast, ou de a câmera expor serviços nas portas TCP pesquisadas. Não há garantia de 100% em roteadores com isolamento de clientes.
- Em redes superiores a 1022 hosts, a busca TCP é limitada ao bloco `/24` local para não sobrecarregar a rede.

## 1.4.0+10 | 2026-10-11

- Versão de entrada recebida para manutenção; reprodução RTSP, consulta ONVIF por IP, fallback de fluxos e adição manual.

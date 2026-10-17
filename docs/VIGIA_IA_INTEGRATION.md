# Descobertas do Minha Câmera úteis para o Vigia IA

Atualizado em **1.4.36+46** (2026-10-15).

Este material serve como registro técnico: **o Vigia IA 1.0.244 recebe módulo MCRP independente**.

## Descoberta automática e estabilidade (1.4.1)

- `WsDiscovery.kt`: emitir `Probe` ONVIF UDP/3702, interpretar `ProbeMatch` e `XAddrs`, respeitar Wi-Fi e multicast.
- `NetUtil.kt` e `ScanActivity.kt`: consultar rede/máscara IPv4 em vez de presumir `/24`; limitar simultaneidade e tempo de varredura; juntar ONVIF e resultados TCP por IP/porta; cancelar trabalhos anteriores.
- `OnvifClient.kt`: obter perfis e URLs RTSP reais da câmera, evitando caminhos presumidos quando ONVIF responde.
- `RtspProbe.kt`: negociar Basic/Digest RTSP, respeitar `Content-Length` em bytes e desafios de autenticação.

## Reprodução e diagnóstico H.264/H.265 (1.4.2)

- `RtspCodec.kt`: detectar o codec **de vídeo** pelo SDP (`m=video` + `a=rtpmap`), ignorando rtpmap de áudio. Não determinar o codec apenas pelo nome do caminho ou pelo retorno HTTP/RTSP 200.
- `ScanActivity.kt`: quando o primário é H.265, testar o caminho secundário antes de salvá-lo como principal; usar H.264 se confirmado pelo SDP. Não presumir que todo substream é H.264.
- `PlayerActivity.kt`: lição histórica da 1.4.2 era iniciar Media3; a 1.4.26 alterou essa escolha após crash não tratado em `RtspClient.java:544` do Media3 1.3.1 ao processar `CSeq` ausente. Iniciar LibVLC diretamente até validar uma versão do Media3 que trate respostas RTSP malformadas sem encerrar o processo. Prever timeout, cancelamento de callbacks obsoletos e liberação de recursos.
- Nunca publicar URLs RTSP com credenciais em logs, relatórios de erro ou analytics. Preferir um armazenamento protegido por keystore/criptografia com processo de migração dos dados legados.

## Atenções na adaptação para Flutter/Android

1. Extrair descoberta e negociação de rede em um módulo Android testável antes de expor por platform channels ao Flutter.
2. Medir tamanho final do pacote, ABIs suportadas, consumo de CPU/GPU, memória, bateria, latência, aquecimento e autonomia ao usar LibVLC. Considerar distribuição ABI-specific em App Bundle.
3. Priorizar H.264 quando qualidade, recursos do dispositivo e uso contínuo favorecerem eficiência; HEVC deve ter testes reais em cada plataforma/hardware de interesse.
4. Câmeras RTSP não anunciam necessariamente ONVIF; manter cadastro manual e os dois mecanismos de descoberta.
5. Para streams RTSP com SDP problemático, comparar VLC vs Media3 e registrar o SDP **com IP/credenciais mascarados** antes de decidir por workaround.
6. Uma camada Flutter baseada em libVLC precisa de uma integração nativa adequada para gerenciar Surface, ciclo de vida e áudio. Não copiar diretamente código `PlayerActivity` para o app Flutter.

## Estado de validação

Os testes simulados da 1.4.2 validam o **parser SDP e a sondagem RTSP**, não a decodificação real H.265 ou a integração Android LibVLC. Testes em câmeras reais são obrigatórios antes de portar ao Vigia IA.


## Ergonomia de cadastro e suporte operacional (1.4.3)

- Cadastro e edição mais estruturados reduzem erro humano ao configurar IP, porta, usuário, senha e caminho.
- Recurso de copiar o RTSP completo com credenciais acelera testes cruzados com players externos, NVRs e outras telas do ecossistema.
- Sugestões rápidas de caminho com opção **Default** servem como modelo para uma UX híbrida no Vigia IA: simples para iniciantes, mas sem bloquear ajustes manuais.
- O tratamento explícito da senha (ver/copiar/colar) melhora a usabilidade, mas reforça a necessidade de segurança reforçada no armazenamento quando a abordagem for levada ao Vigia IA.

## Compatibilidade de ferramentas Android (1.4.4)

- O build #7 do Minha Câmera falhou porque `libvlc-all:3.7.7` requer `compileSdk >= 36`, mas o projeto estava em API 34/AGP 8.5.2.
- Para o protótipo Kotlin/Android, foram alinhados AGP 8.11.1, Gradle 8.13 e Kotlin 2.2.20 e instalada a plataforma 36 no CI. Na integração futura do Vigia IA, avaliar a matriz de versões do Flutter, do plugin Android Gradle e dos plugins nativos, sem copiar cegamente os números desta release.
- `compileSdk` é independente de `targetSdk`; manter o comportamento de runtime conhecido até validar eventuais exigências de atualização.
- A correção do padrão Regex de ocultação de URL reforça a importância de incluir compilação completa no CI antes de declarar uma versão validada.


## Integração contínua Android (1.4.5)

- Não presumir que executáveis do Android SDK estejam no `PATH` de imagens de runners. Instalar e inicializar `sdkmanager` explicitamente antes de solicitar plataformas Android.
- Verificar as pré-condições do ambiente (SDK, JDK, Gradle e caminhos) no CI, com erros visíveis, em vez de mascará-los com `|| true`.
- O build #8 não iniciou o compilador Kotlin/Android; seu erro não permite concluir nada sobre estabilidade de RTSP/HEVC ou do player no dispositivo.


## Desempenho e cache da compilação Android (1.4.6)

- O build #9 do Minha Câmera compilou em 2m08s no Gradle, mas a execução do GitHub Actions foi cancelada antes de publicar o APK.
- Não tomar aumento de tempo do CI como indicação automática de problema de runtime do LibVLC; são métricas diferentes.
- Testar cache Gradle (`org.gradle.caching=true`, `setup-gradle@v6`) entre builds completos antes de remover dependências necessárias ao H.265.
- Guardar tempos de diferentes builds (com e sem cache) para mensurar otimizações antes de transportar a estratégia ao Vigia IA.

## Distribuição por arquitetura (1.4.7)

- Reduzir APKs por ABI preserva LibVLC e codecs; não confundir remoção de cópias para outras CPUs com remoção de funcionalidades.
- No Flutter, avaliar `flutter build apk --split-per-abi` e os binários de todos os plugins (inclusive IA), verificando o APK final; não copiar o Gradle Kotlin deste projeto automaticamente.
- Conferir ABIs efetivamente suportadas por cada dependência nativa, certificado, applicationId e versionCode antes de distribuir variantes.
- Para APKs diretos, manter código sequencial comum. Não usar offsets que bloqueiem a migração de Universal para ABI ou vice-versa.
- O versionCode crescente não resolve uma chave de assinatura diferente. Preservar a chave original é requisito para atualizar e manter dados.
- Centralizar testes/assemble em uma execução e publicar artefatos por arquivo evita recompilar todo o app por arquitetura.
- Tamanhos reais e limites de testes deste projeto estão em BUILD_VALIDATION.md; não constituem medidas do Vigia IA.

## Interface e gravação local (1.4.8)

- Expor sugestões de RTSP em dropdown editável evita dezenas de chips e conserva caminhos personalizados.
- STATE_READY/Playing não garante quadro visível. No Media3 usar onRenderedFirstFrame; no LibVLC consultar displayedPictures da mídia, mantendo timeout/fallback.
- Duas lentes podem ser um único vídeo empilhado. Recortar o viewport não cria dois streams nem controle PTZ; manter opção de imagem completa.
- LibVLC 3.7.7 fornece record(directory), record(null) e RecordChanged. Usar confirmação nativa e não mostrar sucesso apenas pelo clique.
- Arquivos nativos podem ter nomes derivados do URL. Manter em sessão privada e renomear antes de exportar; preservar extensão/conteúdo, incluindo HEVC.
- FileProvider + ACTION_CREATE_DOCUMENT oferecem compartilhamento/exportação sem acesso amplo ao armazenamento.
- Separar gravação local da reprodução de histórico no cartão/NVR e de serviços 24h. Sessão de Activity termina em onStop; um serviço exige outro ciclo de vida e validação própria.
- Biblioteca por dia e atualização após finalização devem observar conclusão assíncrona, sem bloquear a thread do player.

## Rotação, enquadramento e identidade (1.4.9)

- Com Activity `configChanges`, recursos `layout-land` não são reinflados: reorganizar controles existentes ao receber rotação preserva player e gravação.
- FIT do fluxo inteiro e FIT da metade selecionada têm geometrias diferentes. Calcular pelo tamanho do decoder, SAR e orientação; nunca usar zoom para isolar uma lente. Testar invariantes de contenção nas duas orientações.
- GetDeviceInformation informa identidade de firmware, frequentemente genérica. GetServices/GetCapabilities anunciam PTZ, recording/search/replay, mas anúncio não é teste funcional ou certificação.
- A câmera JT-9686 do usuário funciona com Yoosee e RTSP, e permite movimento/histórico no aplicativo original. Isso não comprova que esses recursos estejam expostos no ONVIF. Não determinar a lente móvel pelo nome do modelo; é necessária configuração de fontes/perfis e teste funcional separado.
- Não adicionar SDK de fornecedor ou protocolo presumido antes de evidência do dispositivo. Consulta somente leitura primeiro; credenciais de ONVIF e RTSP podem diferir.

## Minha Câmera 1.4.18: indicação de bateria no vídeo

`PhoneBatteryTelemetry` lê nível e temperatura **da bateria local** pelo Android. `PhoneBatteryOverlay` aplica selo NV21 uma vez antes dos dois destinos, MJPEG e gravação MP4. Para o Vigia IA, considerar overlay opcional na visualização ou telemetria separada, pois o selo irreversível nos pixels pode não atender usos em que é necessário obter a imagem original. A função não foi incorporada ao Vigia IA.

## Crash de resposta RTSP Media3 1.3.1 (Minha Câmera 1.4.26)

- Log real: `thread=ExoPlayer:Playback`, `NullPointerException`, `RtspClient$MessageListener.handleRtspResponse:544`. Na versão 1.3.1, a linha usa `checkNotNull` sobre o cabeçalho `CSeq` da resposta.
- `try/catch` em `ExoPlayer.prepare()` e `Player.Listener.onPlayerError` não contêm exceções não tratadas no `HandlerThread` do cliente RTSP; um fallback pós-falha não chega a ser executado.
- Mitigação no app: LibVLC como motor exclusivo acionável para RTSP, com tentativa de URLs alternativas, PiP e gravação preservados. Media3 permanece desabilitado até atualização/testes de protocolos RTSP irregulares.
- Investigar com captura RTSP redigida sem segredos antes de atribuir definitivamente a causa ao firmware da câmera. Não modificar automaticamente o Vigia IA.

## Câmera com tela apagada e telemetria desacoplada (1.4.27+37)

- Isolar câmera e MJPEG em ForegroundService `camera` com `SurfaceTexture` de vida útil independente da Activity; iniciar enquanto câmera está autorizada e Activity visível. Executar teste prolongado em dispositivo OEM com tela apagada, sem assumir que `SurfaceTexture` não drenada terá desempenho idêntico em todos os aparelhos.
- Manter WakeLock parcial + WiFiLock durante captura com tela apagada, liberando após finalizar MP4; medir consumo energético real para comparar com tela ativa.
- Telemetria no cabeçalho HTTP MJPEG, desenhada só no receptor em TextView configurável; não sobrepor NV21 antes da gravação H.264 para evitar selos irremovíveis.
- Câmera dupla segue vinculada à Activity e não deve ser anunciada como capturável com tela apagada.

- Comando POST autenticado por token em rede local `control/<token>/record/start|stop`, condicionado a serviço ativo; não expor comando sem autenticação e considerar TLS para redes não confiáveis.

## Codec experimental e métricas (1.4.32+42)
O transmissor permite MJPEG legado em `/live/TOKEN` e H.264/AVC experimental Annex-B por HTTP em `/h264/TOKEN`, com o mesmo token privado. O receptor Minha Câmera utiliza LibVLC com `demux=h264` (não RTSP). Consulte `GET /control/TOKEN/status` para `X-Stream-Codec`, cronômetro e parâmetros, `POST /control/TOKEN/codec/mjpeg` ou `/codec/h264` para alternar e `GET /control/TOKEN/diagnostic` para relatório sem credenciais. O Vigia IA deverá tratar codecs separados, detectar erros, não assumir compatibilidade H.264 até teste real e preservar MJPEG como fallback.

## Metadados e controles em 1.4.33+43
O GET /control/<token>/status inclui X-Device-Model e X-Resolution quando disponibilizados e sanitizados pelo transmissor. Comandos POST podem responder 409 com X-Control-Reason: recording-active / unsupported-mode / command-rejected. O futuro receptor Vigia IA deve mostrar a causa sem reexecutar automaticamente ações destrutivas. Acessos continuam exclusivamente na LAN e exigem token.

## Aprendizado RTSP da versão 1.4.48

Separar detecção de porta TCP de confirmação do serviço RTSP. `OPTIONS` com resposta `RTSP/` indica serviço reconhecível; ausência de resposta não prova RTSP e HTTP não deve ser classificado como vídeo. O `DESCRIBE` válido confirma metadados SDP, mas **não garante decodificação**: confirmar o primeiro quadro real antes de atualizar um cadastro. Preservar credenciais e endereços privados nos diagnósticos exportados. ONVIF é opcional e sua ausência não confirma falta de serviços proprietários.

## Aprendizado 1.4.55 (receptor / retrovisor)

- Tela cheia pode ser aplicada por realocação do mesmo contêiner de vídeo para uma camada imersiva, sem reiniciar MJPEG nem gravador remoto.
- Corrigir a orientação local do MJPEG com matriz da ImageView (90°/180°/270°, ajuste de proporção e espelhamento) antes de transformar ou recomprimir frames; o caminho de LibVLC deve ser validado separadamente.
- Os comandos de gravação e foto sobrepostos em modo paisagem devem continuar usando endpoints remotos existentes e estado confirmado pelo transmissor.
- Exportação de diagnóstico via Storage Access Framework permite salvar TXT sem permissões amplas de armazenamento; mascarar credenciais antes de oferecer exportação.

## Aprendizado v1.4.59: tela apagada e zoom remoto

A troca de uma câmera mantida pela Activity para um serviço de câmera em segundo plano pode bloquear a UI se a superfície EGL for preparada na thread principal e pode falhar na reabertura imediata de recurso/porta. Preparar EGL em worker, habilitar SO_REUSEADDR **antes** do bind, limitar retries, registrar a classe do erro sem credenciais e não confundir visualização local com publicação de quadros. São mitigadores, não garantia de acesso à câmera sob restrições do Android.

O receptor MJPEG deve atualizar periodicamente capacidades do transmissor após handoff, especialmente zoom. Zoom aplicado por Camera.Parameters altera o quadro na origem; quando a API ou o modo não suportar o controle, ampliar apenas no receptor identificando explicitamente "zoom local". Priorizar consumo mínimo no Redmi A3 e confirmar com o aparelho real.

## Biblioteca local e mídia autorizada (Minha Câmera 1.4.61)

- O `MediaStore` deve ser tratado como índice de mídias autorizadas, sem converter `content://` em caminhos absolutos nem tentar acesso a arquivos não concedidos. Incluir tanto as coleções de Imagens/Vídeos quanto Downloads, porque o MP4 da câmera pode ser publicado em `Downloads/MinhaCamera`.
- Gravações RTSP privadas devem ser listadas de uma raiz própria, com URIs `FileProvider`; ao excluir arquivos privados, validar `canonicalFile.parentFile` contra a pasta permitida e exigir confirmação explícita.
- Android 13+ usa permissões separadas de fotos/vídeos; Android 14 também pode fornecer acesso parcial (`READ_MEDIA_VISUAL_USER_SELECTED`). Seleção de arquivos por SAF é alternativa sem acesso amplo.
- Manter indexação, miniaturas e cópias no executor de IO, com cache de imagens limitado; reutilizar instâncias de player apenas durante visualização, liberando-as ao sair da tela.
- Para exclusão de mídia compartilhada no Android 11+, usar `MediaStore.createDeleteRequest` e aceitar cancelamento. A cópia por `ACTION_OPEN_DOCUMENT_TREE` preserva arquivos de origem.
- A implementação 1.4.61 tem testes estáticos e sintáticos locais, mas a execução Android (permissões parciais, codecs, EXIF e exclusões) ainda precisa de validação real antes de reutilizar no Vigia IA.


## 1.4.62: desempenho de galerias Android
- Layout compacto com filtros vetoriais e pesquisa recolhível evita toolbar duplicada.
- Miniaturas decodificadas em dimensões adequadas à densidade real da grade e metadados de vídeo/foto consultados fora da UI com cache limitado.
- Metadados opcionais de tamanho/resolução/duração não devem ser tratados como presentes se o provedor SAF/MediaStore não informar.
- Não replicar no Vigia IA automaticamente sem testes de consumo e scrolling no Android real.

## 1.4.63: cores ARGB Kotlin em UI Android

O erro real do Build APK 62 ocorreu porque `0x990A1422` tem o bit 31 definido e é inferido como `Long` pelo Kotlin, porém `View.setBackgroundColor` exige `Int`. Usar `0x990A1422.toInt()` ou `Color.argb()` e testar essa classe de erro no preflight da CI. Essa nota é para aprendizado técnico; nenhuma alteração foi feita no Vigia IA.

## Aprendizado da Central Multimídia 1.4.66
- Separar aparelho cadastrado de aparelho efetivamente conectado: confirmar biblioteca autenticada via HTTP 200 antes do estado conectado.
- Renomeação deve usar MediaStore com autorização do Android, SAF quando permitido e diretório privado sob controle do app.
- Barras de ações com rolagem horizontal e botões sem insets/sombras padrão evitam cortes em telas pequenas.

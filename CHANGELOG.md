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

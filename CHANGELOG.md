# Histórico de versões

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

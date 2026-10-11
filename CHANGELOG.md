# Histórico de versões

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

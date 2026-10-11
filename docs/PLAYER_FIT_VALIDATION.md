# Validação 1.4.9+19 — rotação, lentes e identificação

## Código de referência

Remote main: `228f003`, versão 1.4.8+18 importada pelo GitHub Manager. O código do player foi comparado com o ZIP anterior e era idêntico. Capturas enviadas mostram controles em duas linhas mesmo em horizontal e bordas cortadas na metade selecionada.

## Causas e correções

A Activity declara orientação/screenSize em configChanges: o Android chama onConfigurationChanged e não reinfla layout-land. A correção reorganiza os mesmos controles nesta chamada, preservando listeners, player e sessão. Em horizontal, quatro botões ocupam uma linha; barra superior 48dp, sem subtítulo. Em retrato, duas linhas e barra de 56dp. Ícones usam textStart para acompanhar o texto.

O modo anterior aumentava a altura do container pelo tamanho da tela e usava ZOOM/FIT_SCREEN. Isso não considera a proporção de uma lente e pode cortar suas bordas. Agora VideoGeometry calcula FIT da metade por tamanho do decoder, SAR e orientação. Um videoClip centralizado tem o tamanho da metade; o vídeo completo fica dentro dele com altura dupla e deslocamento 0 ou -alturaDaMetade. Apenas a outra lente é ocultada; a metade escolhida cabe inteira. Imagem inteira também usa FIT. Barras são necessárias quando a proporção difere da tela. A gravação continua salvando o fluxo original.

## Identificação somente leitura

Menu do player: Identificar câmera e serviços. WS-Discovery procura XAddr; a consulta usa GetDeviceInformation, GetServices e GetCapabilities (All), com autenticação/ajuste de relógio do cliente existente. Exibe fabricante, modelo, firmware e anúncio de serviços PTZ, recording, search e replay. Não retorna senhas nem serial do dispositivo no relatório. XML rejeita DTD/entidades e limita tamanho na análise.

A resposta pode ser genérica. Não anunciar serviço não significa ausência do recurso. Anunciar PTZ não comprova funcionamento ou qual lente possui motor. A consulta não move a câmera, não implementa controles PTZ e não lê o histórico do cartão/nuvem Yoosee. Credenciais ONVIF podem diferir de RTSP; o cliente usa as cadastradas na URL. Não foi assumido fabricante pela referência JT-9686.

Referências oficiais: [operações ONVIF](https://www.onvif.org/onvif/ver20/util/operationIndex.html), [especificações e serviços](https://www.onvif.org/profiles-specifications-new/). API Java examinada no sources.jar oficial do LibVLC 3.7.7: getCurrentVideoTrack, width/height, sarNum/sarDen, orientation.

## Testes e compilação reais

Uma execução Gradle 8.13/JDK17: `testProtocol testReleaseUnitTest assembleRelease --no-daemon --build-cache --console=plain --stacktrace`, com init script externo de validação sem assinatura. BUILD SUCCESSFUL em **3min46s**, 48 tarefas executadas, ambiente sem cache inicial. Workflow e cache não foram alterados e continuam com uma execução Gradle.

Nove verificações aprovadas:
1. Geometria: 4 áreas de tela × 5 proporções × duas metades; contenção integral, proporção preservada, orientação e parâmetros inválidos.
2. Identidade ONVIF: fabricante/modelo/firmware, serviços anunciados, ausência de serviços, fallback capabilities, rejeição DTD e servidor HTTP simulado com quatro consultas sem comando de movimento.
3. Finalização de arquivos privados de gravação sem nomes sensíveis e sem alteração de bytes.
4. Codec SDP somente vídeo.
5. RTSP DESCRIBE H.265.
6. Máscaras de rede /24, /23 e limite /16.
7. RTSP UTF-8 Content-Length/Digest.
8. WS-Discovery XAddr e RelatesTo.
9. ONVIF XAddr anunciado e perfil H.264.

`testReleaseUnitTest` é NO-SOURCE; não existem testes JUnit Android na variante. XML, metadados de versão, configuração e diff --check aprovados. Não houve teste em dispositivo/emulador nesta versão nem acesso à câmera física, movimento real ou reprodução histórica. Os testes de geometria não substituem inspeção de SurfaceView e rotação em aparelho.

## APKs e assinatura

Cinco APKs release **não assinados** foram gerados e conferidos: CRC ZIP, duplicatas, alinhamento, applicationId com.minhacamera, versionName 1.4.9, versionCode 19, minSdk 24, ABIs exatas, três bibliotecas LibVLC por ABI, igualdade de código/recursos e de bibliotecas ante Universal.

| ABI | MB decimais | Redução |
|---|---:|---:|
| Universal | 217,60 | 0% |
| arm64-v8a | 62,74 | 71,17% |
| armeabi-v7a | 47,90 | 77,99% |
| x86_64 | 70,97 | 67,39% |
| x86 | 58,31 | 73,20% |

A chave privada dos builds anteriores não está neste novo ambiente. Não se pode recuperar uma chave privada do APK/certificado. Para respeitar a restrição de não criar chave substituta, um init script **externo ao repositório** desabilitou assinatura somente nesta compilação de validação. app/build.gradle.kts mantém a configuração original. Nenhum keystore/Secret novo foi criado; apksigner confirma ausência de assinatura. Estes APKs não são instaláveis diretamente e não comprovam atualização sobre a instalação anterior. A distribuição instalável precisa ser compilada/assinada com a chave original; usar um debug keystore de outro ambiente pode produzir assinatura diferente. Todos mantêm código sequencial 19, sem offsets ABI.

## Publicação

GitHub recusou nova tentativa em POST /repos/adriedsonlemoz/Minha-Camera/git/trees: **403 Resource not accessible by integration**. Nenhuma atualização remota ou workflow remoto foi realizado. O ZIP inclui código completo, instruções, changelog e notas de integração Vigia IA, pronto para importar pelo GitHub Manager. Não há credenciais reais, arquivos de build ou chaves no ZIP fonte.

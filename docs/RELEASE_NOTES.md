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

# Minha Câmera

Aplicativo Android nativo em Kotlin para ver câmeras IP via RTSP. Busca câmeras na rede Wi-Fi, testa automaticamente os caminhos de vídeo (Intelbras, Hikvision, Tapo e outras), ONVIF, login Digest/Basic, adição manual por IP e gera o APK com GitHub Actions. Player Media3/ExoPlayer.

## Recursos

- **Buscar câmeras:** varre a faixa de IP do Wi-Fi (portas RTSP 554 e 8554) e lista os dispositivos encontrados. Se a primeira busca não achar nada, faz uma segunda tentativa sozinha.
- **ONVIF:** o app pergunta à própria câmera qual é o endereço exato do vídeo (portas 80, 8080, 2020 e outras), sem precisar adivinhar.
- **Teste automático de caminhos:** se o ONVIF não responder, toque na câmera, informe usuário (já vem `admin`) e senha, e o app testa os caminhos de vídeo mais comuns até achar o que funciona. Para cedo se a senha estiver errada, para não bloquear a câmera.
- **Adicionar manualmente:** o botão **+** já vem com o começo do IP da sua rede (ex.: `192.168.100.`); é só completar. Também aceita uma URL `rtsp://...` completa no campo IP.
- **Player ao vivo:** RTP sobre TCP e buffer pequeno para baixa latência.
- **Lista de câmeras:** toque para abrir, segure para **editar** (nome e endereço) ou excluir. A senha não aparece na lista.
- **Fluxos alternativos:** o app guarda também o fluxo secundário da câmera e, se o principal falhar, troca sozinho para ele (preferindo H.264).
- **Erros em português:** mensagens claras com a solução, e os detalhes técnicos em letra pequena abaixo.
- Seta e botão **Voltar** em todas as telas de busca.

## Como usar

1. Deixe o RTSP ativado na câmera e o celular no mesmo Wi-Fi.
2. Toque em **Buscar câmeras** (ou em **+** para digitar o IP).
3. Informe a senha da câmera (a mesma do RTSP/NVR; o usuário padrão é `admin`).
4. A câmera é salva e aparece na tela inicial.

Formato manual da URL: `rtsp://usuario:senha@IP:554/caminho` (o caminho varia por marca).

## Gerar o APK pelo GitHub Actions

1. Envie o conteúdo do projeto para a branch `main` do seu repositório.
2. O workflow `.github/workflows/build-apk.yml` compila a cada push (ou manualmente em **Actions > Build APK > Run workflow**).
3. Em **Actions > execução > Artifacts** há um único item: o APK solto, sem ZIP, com o nome `<NomeDoApp>-<versão>.apk`, sem acento e sem espaço, por exemplo `MinhaCamera-1.3.1.apk`.

O nome vem de `app_name` em `app/src/main/res/values/strings.xml` e a versão de `versionName` em `app/build.gradle.kts` (suba a cada entrega). O APK sai assinado com a chave de debug, então instala direto.

## Limitações

- **H.265 (HEVC):** muitas câmeras enviam H.265 sem o parâmetro `sprop-vps` no SDP e o player Media3 não abre esse caso (erro "missing sprop-vps"). Solução: mudar a codificação do vídeo da câmera para **H.264** no app dela, ou usar o fluxo secundário.

## Desenvolvimento

Abra a pasta no Android Studio; ele baixa o Gradle e sincroniza sozinho.

- Linguagem: Kotlin · minSdk 24 · targetSdk 34
- Player: AndroidX Media3 (ExoPlayer + RTSP)
- Pacote: `com.minhacamera`

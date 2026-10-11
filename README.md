# Minha Câmera

Aplicativo Android nativo em Kotlin para ver câmeras IP via RTSP. Busca câmeras na rede Wi-Fi, testa automaticamente os caminhos de vídeo (Intelbras, Hikvision, Tapo e outras), suporta login Digest/Basic, adição manual por IP e gera o APK com GitHub Actions. Player Media3/ExoPlayer.

## Recursos

- **Buscar câmeras:** varre a faixa de IP do Wi-Fi (portas RTSP 554 e 8554) e lista os dispositivos encontrados. Se a primeira busca não achar nada, faz uma segunda tentativa sozinha.
- **Teste automático de caminhos:** toque na câmera, informe usuário (já vem `admin`) e senha, e o app testa os caminhos de vídeo mais comuns até achar o que funciona. Para cedo se a senha estiver errada, para não bloquear a câmera.
- **Adicionar manualmente:** o botão **+** já vem com o começo do IP da sua rede (ex.: `192.168.100.`); é só completar. Também aceita uma URL `rtsp://...` completa no campo IP.
- **Player ao vivo:** RTP sobre TCP e buffer pequeno para baixa latência.
- **Lista de câmeras:** toque para abrir, segure para excluir. A senha não aparece na lista.
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
3. Em **Actions > execução > Artifacts** há um único item: o APK solto, sem ZIP, com o nome `<nome do app> V<versão>.apk`, por exemplo `Minha Câmera V1.2.2.apk`.

O nome vem de `app_name` em `app/src/main/res/values/strings.xml` e a versão de `versionName` em `app/build.gradle.kts` (suba a cada entrega). O APK sai assinado com a chave de debug, então instala direto.

## Desenvolvimento

Abra a pasta no Android Studio; ele baixa o Gradle e sincroniza sozinho.

- Linguagem: Kotlin · minSdk 24 · targetSdk 34
- Player: AndroidX Media3 (ExoPlayer + RTSP)
- Pacote: `com.minhacamera`

# Minha Câmera

**Versão atual: 1.4.7 (versionCode 17)** · Kotlin / Android · Projeto auxiliar de investigação técnica para o **Vigia IA**. Veja [integração técnica com o Vigia IA](docs/VIGIA_IA_INTEGRATION.md).

Aplicativo para cadastrar e reproduzir câmeras IP RTSP. A versão 1.4.7 gera APKs por arquitetura, mantendo ONVIF, RTSP, H.264/H.265, Media3 e LibVLC. Consulte [CHANGELOG.md](CHANGELOG.md), [notas de release](docs/RELEASE_NOTES.md) e [validação do build](docs/BUILD_VALIDATION.md).

## Descoberta automática

- **ONVIF WS-Discovery:** envia probes multicast UDP a `239.255.255.250:3702`, recebe `ProbeMatch`, identifica o IP de quem respondeu e aproveita os `XAddrs` para consultar o serviço da câmera com credenciais.
- **Varredura TCP complementar:** usa prefixo/máscara IPv4 obtidos do Wi-Fi para testar 554, 8554, 10554, 8555 e 7447. Consolida por IP sem perder outras portas do mesmo dispositivo.
- **Proteção da rede:** limita a concorrência a 20 tarefas; em sub-redes com mais de 1022 hosts, varre por TCP apenas o bloco /24 local, informando essa limitação ao usuário.
- **Fallback:** se não encontrar dispositivos, realiza uma segunda rodada. Multicast depende das regras de roteador, isolamento do Wi-Fi e configuração ONVIF da câmera.

## Recursos de câmera

- **Consulta ONVIF Media:** com usuário e senha, consulta os perfis e URLs RTSP usando a URL anunciada na descoberta ou portas ONVIF conhecidas.
- **RTSP:** tenta caminhos comuns para Intelbras, Hikvision, Tapo e outras, com autenticação Digest/Basic. Lê respostas em bytes conforme `Content-Length`.
- **Manual:** permite cadastrar IP/porta/caminho ou URL RTSP completa pelo botão `+`.
- **Player:** usa Media3/ExoPlayer primeiro e, caso todos os fluxos falhem, tenta LibVLC 3.7.7 para compatibilidade adicional com RTSP/HEVC. O menu da tela de vídeo permite alternar manualmente os motores. Falhas H.265 ainda são possíveis dependendo do SDP, do codec enviado e do decodificador.
- **Codec por SDP:** interpreta `RTSP DESCRIBE`; prioriza o secundário H.264 somente quando confirmado, em vez de assumir que o nome do caminho indica o codec.
- **Histórico:** menu superior "Novidades e versões".

## Como usar

1. Ligue a câmera e conecte o telefone ao mesmo Wi-Fi, sem isolamento entre clientes.
2. Abra **Buscar câmeras** ou **+** para configurar manualmente.
3. Toque na câmera encontrada, informe as credenciais RTSP/ONVIF e teste. Os formulários permitem ver, copiar e colar a senha e escolher sugestões rápidas de caminho RTSP.
4. A lista principal mostra cartões com ações rápidas para abrir, copiar o endereço RTSP completo e editar/excluir a câmera.
5. Se a descoberta falhar, verifique as portas da câmera, Wi-Fi e ONVIF, ou use `rtsp://usuario:senha@IP:554/caminho`.

## GitHub Actions

O workflow `.github/workflows/build-apk.yml` roda em push nas branches `main`/`master` ou manualmente, prepara Android SDK 36 e executa `gradle testProtocol testReleaseUnitTest assembleRelease` uma única vez, com cache. Publica cada APK individualmente, sem ZIP, em **Actions > Artifacts**:

- `MinhaCamera-1.4.7-universal.apk`: todas as quatro ABIs, para distribuição geral.
- `MinhaCamera-1.4.7-armeabi-v7a.apk`: Android ARM de 32 bits.
- `MinhaCamera-1.4.7-arm64-v8a.apk`: Android ARM de 64 bits.
- `MinhaCamera-1.4.7-x86_64.apk`: Android Intel de 64 bits/emuladores.
- `MinhaCamera-1.4.7-x86.apk`: Android Intel de 32 bits/emuladores legados; mantido para preservar suporte anterior.

Todos têm `com.minhacamera`, versão 1.4.7, código 17 e a mesma assinatura dentro do build. Escolha pela ABI do **sistema Android**, não só pela CPU. O relatório `apk-report.md` e `SHA256SUMS` acompanham os artefatos. O verificador compara também os bytes das bibliotecas com o Universal, sem recompilar o app por ABI.

A configuração release continua usando a chave debug do ambiente. **Não há garantia de continuidade com a assinatura de execuções anteriores**. Para atualização sem desinstalar/perder dados, recuperar a chave original e comparar seu certificado com o APK instalado; não basta trocar por uma nova chave. Nenhum Secret ou keystore foi adicionado. O build local desta entrega foi aprovado, mas **seu certificado difere do APK 1.4.6 do Actions**; portanto os APKs locais são de validação e não atualizam aquela instalação. A publicação remota foi impedida por 403. Veja [resultados e impedimentos](docs/BUILD_VALIDATION.md).
O cache de tarefas é habilitado com `org.gradle.caching=true`; o `gradle/actions/setup-gradle@v6` restaura/salva o Gradle User Home após um job concluído. A execução não deve ser cancelada assim que aparecer `BUILD SUCCESSFUL`, porque ainda faltam nomear/publicar o APK e finalizar o cache. O primeiro build pode continuar demorado, e mudanças no projeto invalidam parte do cache. A mensagem `BUILD SUCCESSFUL in 2m 8s` do build #9 foi seguida de cancelamento externo, antes de publicar o APK.

Para atualizar, siga [docs/VERSIONING.md](docs/VERSIONING.md), execute `python3 scripts/check_version.py`, `python3 scripts/check_build_config.py` e, onde houver `kotlinc`, `bash scripts/test_protocol.sh`; depois sincronize versões, documentos e histórico e confirme o build no GitHub.

## Limitações e segurança

- O sucesso da descoberta não garante que o dispositivo exponha um stream RTSP suportado.
- ONVIF pode anunciar IP/porta diferentes dos esperados; o app usa o IP do dispositivo que respondeu ao probe.
- Senhas em URLs RTSP ainda são armazenadas em preferências locais sem criptografia. O backup Android foi desabilitado em 1.4.2 como mitigação; a proteção com criptografia continua pendente. Evite compartilhar exportações/relatórios que possam conter URLs.
- LibVLC adiciona bibliotecas nativas e aumenta o download/armazenamento; sua distribuição está sujeita aos termos aplicáveis da licença LGPL (ver [VideoLAN](https://www.videolan.org/legal.html)).
- Para testar H.265 real é necessário compilar e executar no Android. Os testes simulados não são garantia de compatibilidade.

Pacote: `com.minhacamera` · minSdk 24 · targetSdk 34 · compileSdk 36 · AGP 8.11.1 · Gradle 8.13 · Kotlin 2.2.20 · Media3 1.3.1 · LibVLC 3.7.7.

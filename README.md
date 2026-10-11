# Minha Câmera

**Versão atual: 1.4.1 (versionCode 11)** · Kotlin / Android · Projeto auxiliar de investigação técnica para o **Vigia IA**.

Aplicativo para cadastrar e reproduzir câmeras IP RTSP. A versão 1.4.1 concentra as alterações na descoberta automática e autenticação. Consulte [CHANGELOG.md](CHANGELOG.md) e [notas de release](docs/RELEASE_NOTES.md).

## Descoberta automática

- **ONVIF WS-Discovery:** envia probes multicast UDP a `239.255.255.250:3702`, recebe `ProbeMatch`, identifica o IP de quem respondeu e aproveita os `XAddrs` para consultar o serviço da câmera com credenciais.
- **Varredura TCP complementar:** usa prefixo/máscara IPv4 obtidos do Wi-Fi para testar 554, 8554, 10554, 8555 e 7447. Consolida por IP sem perder outras portas do mesmo dispositivo.
- **Proteção da rede:** limita a concorrência a 20 tarefas; em sub-redes com mais de 1022 hosts, varre por TCP apenas o bloco /24 local, informando essa limitação ao usuário.
- **Fallback:** se não encontrar dispositivos, realiza uma segunda rodada. Multicast depende das regras de roteador, isolamento do Wi-Fi e configuração ONVIF da câmera.

## Recursos de câmera

- **Consulta ONVIF Media:** com usuário e senha, consulta os perfis e URLs RTSP usando a URL anunciada na descoberta ou portas ONVIF conhecidas.
- **RTSP:** tenta caminhos comuns para Intelbras, Hikvision, Tapo e outras, com autenticação Digest/Basic. Lê respostas em bytes conforme `Content-Length`.
- **Manual:** permite cadastrar IP/porta/caminho ou URL RTSP completa pelo botão `+`.
- **Player:** usa Media3/ExoPlayer e tenta fluxos alternativos ao detectar erro. Algumas câmeras H.265/HEVC ainda geram `missing sprop-vps`; configure fluxo H.264 ou secundário se necessário.
- **Histórico:** menu superior "Novidades e versões".

## Como usar

1. Ligue a câmera e conecte o telefone ao mesmo Wi-Fi, sem isolamento entre clientes.
2. Abra **Buscar câmeras** ou **+** para configurar manualmente.
3. Toque na câmera encontrada, informe as credenciais RTSP/ONVIF e teste.
4. Se a descoberta falhar, verifique as portas da câmera, Wi-Fi e ONVIF, ou use `rtsp://usuario:senha@IP:554/caminho`.

## GitHub Actions

O workflow `.github/workflows/build-apk.yml` roda em push nas branches `main`/`master` ou manualmente, compila `assembleRelease` e envia o arquivo `MinhaCamera-1.4.1.apk` em **Actions > Artifacts**. O release usa a chave de debug do ambiente de build. O ZIP de código-fonte não contém um APK pré-compilado.

Para atualizar, siga [docs/VERSIONING.md](docs/VERSIONING.md), execute `python3 scripts/check_version.py` e, onde houver `kotlinc`, `bash scripts/test_protocol.sh`; depois sincronize versões, documentos e histórico e confirme o build no GitHub.

## Limitações e segurança

- O sucesso da descoberta não garante que o dispositivo exponha um stream RTSP suportado.
- ONVIF pode anunciar IP/porta diferentes dos esperados; o app usa o IP do dispositivo que respondeu ao probe.
- Senhas em URLs RTSP são armazenadas em preferências locais sem criptografia no projeto de entrada; correção pendente. Evite exportar dados do app e compartilhá-los.
- Compatibilidade H.265 ampliada via LibVLC ainda não implementada.

Pacote: `com.minhacamera` · minSdk 24 · targetSdk 34 · Media3 1.3.1.

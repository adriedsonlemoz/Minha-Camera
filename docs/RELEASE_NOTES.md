# Minha Câmera 1.4.2 (versionCode 12)

**Data:** 2026-10-11

## Alterações

- **Player com dois motores:** mantém Media3 para os fluxos compatíveis e, quando todas as URLs falham, tenta automaticamente as mesmas URLs no **LibVLC 3.7.7** (versão estável). É possível selecionar Media3 ou LibVLC no menu do player para testes. Cada motor solta seus recursos ao trocar de fluxo ou fechar a tela.
- **H.265/HEVC:** o LibVLC pode reproduzir streams que provocavam `missing sprop-vps` no Media3, mas **não há garantia** para SDP ou frames inválidos e decodificadores incompatíveis. Se os dois motores falharem, a tela apresenta o diagnóstico, sem expor URLs com credenciais.
- **Identificação de codecs:** a resposta SDP de `RTSP DESCRIBE` agora é lida e interpretada. Se o caminho inicial usar H.265 e o secundário responder com **H.264 confirmado**, o H.264 é salvo como URL prioritária.
- **Recuperação:** não avança duas vezes por callbacks duplicados; timeout de 15 s para inicialização no LibVLC; tentativa das URLs alternativas.
- **Segurança:** backup Android desabilitado para reduzir risco de exportar credenciais armazenadas em URLs RTSP. A criptografia das preferências ainda está pendente.

## Validação

- Executado `bash scripts/test_protocol.sh`: testes simulados de SDP H.264/H.265, seleção somente de faixa de vídeo, leitura `Content-Length`, DESCRIBE H.265, autenticação Digest, WS-Discovery/ONVIF e sub-redes.
- Executado `python3 scripts/check_version.py` para conferir os metadados de versão.
- Verificação estática do XML Android. **Não foi compilado um APK Android**, pois este ambiente não possui Android SDK/Gradle e não pode obter dependências Maven; o código LibVLC depende de compilação e teste real.

## Roteiro de teste no Android

1. Compilar via GitHub Actions (configuração existente). Para instalar como atualização sobre 1.4.1, o APK precisa estar assinado com a mesma chave de assinatura da versão instalada; a chave de debug criada pelo runner pode mudar a cada build.
2. Reabrir uma câmera H.264 conhecida e confirmar a transmissão com Media3.
3. Abrir a câmera que falhava em `sprop-vps`; conferir se alterna para LibVLC e reproduz.
4. Pelo menu, escolher manualmente cada motor e observar erro, latência e aquecimento.
5. Refazer a descoberta ONVIF, testar perfil secundário H.264, sair e entrar repetidamente para verificar recursos nativos.
6. Comparar tamanho do APK (LibVLC inclui bibliotecas nativas), consumo de memória e bateria.

**Integração Vigia IA:** aproveitar a leitura do SDP e a seleção do fluxo antes de avaliar suporte nativo LibVLC em Flutter; medir impacto do tamanho do APK e do consumo de CPU.

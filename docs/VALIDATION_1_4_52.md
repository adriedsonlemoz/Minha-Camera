# Validação 1.4.52+62 — 16/10/2026

## Escopo e condições
- Base: projeto entregue como 1.4.51+61. Alterações incrementais para várias marcas/protocolos.
- A câmera JORTAN JT-9686 / adesivo AJ-A13-1H aparece na embalagem Yoosee, com H.265 anunciado. O firmware e suas portas de streaming não foram identificados em tempo real.
- O log de 1.4.50 comprova RTSP `OPTIONS 200` em 192.168.100.209:554, porém não confirmou `DESCRIBE` autenticado e o LibVLC exibiu zero bytes.

## Implementação
- Roteamento por URI: RTSP (TCP seguido de UDP), HTTP(S) direto, HLS (.m3u8), DASH (.mpd), RTMP(S) e RTP/UDP experimentais.
- `CameraStreamProtocol` valida os esquemas e host antes do cadastro; ONVIF não é considerado mecanismo de reprodução.
- Cadastro manual usa campos de credenciais quando uma URL RTSP ainda não possui login embutido. URLs completas são cadastradas SEM confirmação prévia de reprodução.
- Câmera de celular é identificada exclusivamente por formato/token específico; HTTP genérico abre no LibVLC.
- Varredura TCP inclui 80, 8080 e 5000, classifica o serviço e evita enviar múltiplos DESCRIBE a portas já identificadas como não-RTSP.
- Diagnóstico RTSP exige faixa SDP `m=video`, preserva os estados do desafio Digest e oferece orientação sobre a senha NVR/RTSP.
- Relatório não expõe usuário/senha nem query string e protege caminhos potencialmente sensíveis.
- P2P Yoosee e WebRTC **não implementados**. Exigem SDK/protocolo de sinalização específico. Não anunciar compatibilidade universal.

## Testes locais concluídos
- `python3 -m unittest discover -s tests -p 'test_*.py' -q`: 100 testes aprovados.
- `bash scripts/test_protocol.sh`: simuladores Kotlin/JVM de RTSP/Digest/ONVIF, codecs, câmera de celular e política de repetição aprovados; `CameraStreamProtocolSmoke` 17 casos principais e mais casos negativos.
- `python3 scripts/check_build_config.py`: aprovado.
- `python3 scripts/check_version.py`: aprovado.
- `python3 scripts/check_kotlin_strings.py`: 63 arquivos Kotlin verificados, aprovado.
- `python3 scripts/check_kotlin_imports.py`: 63 arquivos Kotlin verificados, aprovado.
- `python3 scripts/check_android_resources.py`: aprovado, 371 strings.
- `python3 scripts/check_protocol_sources.py`: 19 arquivos Kotlin sincronizados entre Gradle/shell.
- 72 XML analisados por ElementTree, sem erro de estrutura.

## Pendências
- **Sem build Android/Gradle completo local**, sem APK gerado nesta entrega.
- Reproduzir na JT-9686 com NVR/RTSP ativado no Yoosee, conferindo a senha NVR definida pelo usuário. Comparar mesmo endereço no VLC externo sem divulgar a senha.
- Testar cada formato experimental em servidor real; os decoders/protocolos nativos do LibVLC podem não suportar todas as combinações.
- Verificar atualização via APK assinado com keystore estável e implementação de teste de URL direta na interface.

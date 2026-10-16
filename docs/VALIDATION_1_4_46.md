# Validação da versão 1.4.46+56

Data: 2026-10-16

## Alterações
- Player RTSP tenta TCP e UDP por URL, com mudança de LibVLC core/media por tentativa.
- Diagnóstico posterior por `DESCRIBE` da URL cadastrada, sem apresentar usuário, senha ou endereço.
- Rede Wi-Fi local selecionada quando disponível, inclusive redes sem Internet.
- Inalterados fluxo MJPEG/H.264 entre celulares, gravações e configuração de assinatura.

## Verificações locais
- `python3 scripts/check_version.py`, `check_build_config.py`, `check_android_resources.py`, `check_kotlin_imports.py`, `check_kotlin_strings.py`.
- `python3 -m unittest discover -s tests -p 'test_*.py' -q`.
- Compilação JVM de `RtspPlaybackPolicy.kt` e `RtspPlaybackSmoke.kt` com oito asserções.
- Verificação sintática de fontes Kotlin e parse dos XMLs.

## Pendências
- Compilação Android release pelo GitHub Actions e teste físico com RTSP real.
- `DESCRIBE` com SDP 200 não comprova recepção de RTP nem compatibilidade do decoder: conferir o relatório e a URL real no dispositivo.
- Conexão RTSP indisponível, IP diferente ou credenciais inválidas não podem ser corrigidos somente no cliente.

## Resultados desta validação
- `check_version.py`, `check_build_config.py`, `check_android_resources.py`, `check_kotlin_imports.py`, `check_kotlin_strings.py`, `check_protocol_sources.py`: aprovados.
- 80 testes Python: aprovados.
- `scripts/test_protocol.sh`: aprovado com os testes de protocolo existentes e 8 verificações da nova política de fallback.
- Parser Kotlin PSI sobre 47 fontes Kotlin principais: zero erros sintáticos.
- Todos os 71 XMLs em `app/src/main/res`: válidos.
- Não houve compilação Android real nem conexão com câmera física no ambiente.

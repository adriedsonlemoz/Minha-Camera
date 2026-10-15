# Validação do Minha Câmera 1.4.29+39 (2026-10-15)

## Implementação
- Redesenhada a tela do receptor MJPEG com card escuro de vídeo, cabeçalho com nome real da conexão, telemetria compacta separada dos pixels e FPS/resolução medidos.
- Mantidos PiP, modo espelho, inteira/preencher, reconectar e foco/expandir.
- Adicionados giro local 180°, foto do quadro recebido (salva no receptor) e botão de REC remoto existente.
- Adicionado Eco remoto por `POST /control/<token>/eco/enable|disable`, com verificação do token privado da sessão e status `X-Eco` em quadros MJPEG do serviço persistente.
- Menu Mais renovado com ícones e interruptores, detalhes técnicos sob demanda.

## Testes realizados
- `python3 scripts/check_version.py`: versão 1.4.29+39 sincronizada.
- `python3 scripts/check_android_resources.py`: 278 strings verificadas, sem erro de escape/apóstrofo.
- `pytest -q tests/`: 51 testes aprovados, 2 subtestes aprovados.
- `bash scripts/test_protocol.sh`: testes Kotlin/JVM de protocolo concluídos, inclusive REC/Eco autenticados e rejeição de token/método inválido.
- Parser PSI Kotlin: 31 arquivos fonte, nenhum erro sintático.
- Parsing XML: 61 documentos válidos.
- Arquivo ZIP: teste de integridade CRC ao gerar entrega.

## Limitações
- Não houve compilação Android/Gradle nem teste físico do visualizador no Redmi A3 ou no outro celular; só haverá confirmação de UI e comportamento após build e teste em dispositivo.
- `Foto` salva o último JPEG recebido no aparelho receptor, não tira foto com o sensor remoto.
- Eco remoto depende do serviço de câmera persistente e versão atual no transmissor.
- Resolução/FPS são leituras; comandos de zoom, foco, lanterna, troca remota da lente e seleção de resolução/FPS não foram adicionados. O modelo Redmi A3 não é inferido a partir do IP.
- Em Android 7–9 a foto usa a pasta privada do aplicativo; no Android 10+ usa Pictures/MinhaCamera.

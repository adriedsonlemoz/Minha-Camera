# Validação | Minha Câmera 1.4.47+57

Data: 2026-10-16

Mudanças:
- Modo tela cheia automático no visualizador quando orientação do receptor é `auto` e a configuração do Android muda para paisagem. O modo retrato devolve os cartões completos. Controles transparentes já existentes reduzidos a cinco ações.
- Política da rotação de câmera traseira/frontal corrigida com sinal do `OrientationEventListener` e conversão inicial do `Display.rotation` para o mesmo referencial.
- A gravação MP4 mantém rotação fixa enquanto o arquivo está ativo.

Validações executadas:
- Testes Python incluindo `test_landscape_auto_fullscreen_1447.py`.
- Testes Kotlin/JVM com 32 combinações de sensores/ângulos e orientação inicial.
- Preflights de Android resources, imports, strings, build config, versão; parse XML; integridade ZIP.

Pendências:
- Sem build completo no ambiente, pois o fonte fornecido não contém Gradle wrapper e as dependências Android não estão disponíveis localmente.
- Teste real em aparelhos pendente, incluindo modo tela apagada, transição para paisagem/PiP e gravação MP4.

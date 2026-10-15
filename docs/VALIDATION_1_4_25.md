# Validação 1.4.25+35 — 2026-10-15

## Alterações principais
- Receptor com barra mais próxima da tela de transmissão.
- Comandos remotos do receptor para o transmissor: trocar câmera e gravar/parar.
- Estado REC exposto em cabeçalhos MJPEG e refletido na interface do receptor.

## Verificações locais
- Versionamento sincronizado para 1.4.25+35.
- XML de layout do receptor validado.
- Strings Android válidas após inclusão dos novos textos.
- Parser Kotlin e testes específicos: ver relatório entregue junto da resposta.

## Pendências
- Build Android completo no GitHub Actions.
- Teste em dois aparelhos físicos do comando remoto Trocar e Gravar/Parar.

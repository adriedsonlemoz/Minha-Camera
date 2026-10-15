# Validação 1.4.21+31 — 2026-10-15

## Alterações principais
- Telemetria ampliada do transmissor: bateria, temperatura, carregando/não carregando e média de corrente do dia.
- Cabeçalhos MJPEG com telemetria para o receptor.
- Tela do receptor redesenhada em retrato com estilo alinhado ao transmissor e sem elemento central indevido.

## Verificações locais
- Versionamento 1.4.21+31 sincronizado em app/build.gradle.kts, AGENTS, README, CHANGELOG, RELEASE_NOTES e VERSIONING.
- XML principal do receptor revisado manualmente.
- Verificação sintática Kotlin por parser local: pendente de compilação Android/CI.
- Integridade do ZIP: pendente até o empacotamento final.

## Pendências
- Executar build Android completo no GitHub Actions.
- Validar em aparelho real o layout do receptor em pé, a telemetria mostrada e o comportamento como retrovisor.

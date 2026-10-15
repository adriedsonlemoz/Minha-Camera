# Validação 1.4.24+34 — 2026-10-15

## Mudanças

- Espelho horizontal opcional no ImageView, preferências persistidas somente no receptor.
- Foco expande o preview; controle é restaurado por toque na imagem.
- Ocultação de imagem parada em perda de conexão.
- Interface compacta com cinco ações, sem bibliotecas adicionais.

## Verificações

- `scripts/check_version.py`, `scripts/check_build_config.py` e `scripts/check_android_resources.py`.
- Parser PSI Kotlin e XML estruturais.
- Testes de integração visual (`tests/test_viewer_rearview_1424.py`).
- Integridade do ZIP na entrega.

## Pendências

- Gradle Android completo e testes físicos de espelho, foco e reconexão em aparelhos reais.
- Textos queimados nos JPEGs pelo transmissor também são espelhados; a telemetria independente do receptor não é.

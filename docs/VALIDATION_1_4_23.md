# Minha Câmera 1.4.23+33 — correção Build APK 24 (2026-10-15)

## Falha confirmada

AAPT2 falhou na compilação do recurso `novidades_text` em `app/src/main/res/values/strings.xml:142`, com `Invalid unicode escape sequence in string` durante `:app:mergeReleaseResources`. O texto incluído na versão 1.4.21 continha `marca d'água` sem escape Android.

## Alterações

- Substituído o apóstrofo literal pela forma escapada `d\'água` dentro do recurso `novidades_text`.
- Adicionado `scripts/check_android_resources.py` ao pré-build para detectar apóstrofos sem escape, barras invertidas malformadas e escapes unicode inválidos. Adicionados testes unitários.
- Metadados sincronizados na 1.4.23+33 sem modificar keystore, Secrets nem certificado.

## Validação e limites

A checagem local confirma a forma do recurso e a consistência dos metadados; os testes do GitHub Actions e compilação Android completa ainda precisam ser executados na CI. As etapas de protocolo e assinatura passaram nos logs do Build 24 antes dessa falha.

### Testes executados nesta correção

- `scripts/check_version.py` e `scripts/check_build_config.py`: aprovados.
- `scripts/check_android_resources.py`: 223 strings validadas; nenhuma sem escape.
- `tests/test_android_resources.py`: seis testes aprovados.
- `tests/test_signing_preflight.py`: 16 testes aprovados sem tocar na keystore.
- `tests/test_qr_integration.py`, `tests/test_ui_1414.py`, `tests/test_preview_integration.py`, `tests/test_frame_formats_1417.py` e `tests/test_battery_overlay_1418.py`: aprovados.
- `test_preview_integration.py` atualizado para exigir o argumento de telemetria já implementado em `session.publish(encoded, battery)`, em vez de testar a assinatura antiga.
- 51 arquivos XML analisados sintaticamente com ElementTree: válidos.
- Não foi executado build Android completo; o Build APK 24 já havia concluído os testes de protocolos, mas falhado na compilação de resources.

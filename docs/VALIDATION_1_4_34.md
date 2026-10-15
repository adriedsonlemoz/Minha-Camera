# Validação Minha Câmera 1.4.34+44 · 2026-10-15

## Evidência do Build APK 34

- ZIP de logs recebido: `Minha-Camera-Build-APK-34-logs.zip`.
- Workflow parou em `:compileProtocolTests`, antes de `:app:compileReleaseKotlin` e antes de `assembleRelease`.
- Compilador Kotlin/JVM informou `Unresolved reference PhoneAvcAnnexB` em `tests/PhoneAvcAnnexBSmoke.kt`, linhas 5, 8, 10 e 11.
- A classe `PhoneAvcAnnexB.kt` existe no app e no `scripts/test_protocol.sh`, mas faltava na lista de fontes `protocolSources` no Gradle raiz.

## Correção

- Adicionado `PhoneAvcAnnexB` à lista de fontes Kotlin/JVM do Gradle raiz.
- Novo `scripts/check_protocol_sources.py` valida a equivalência das fontes puras Kotlin compiladas pelo Gradle e por `scripts/test_protocol.sh`.
- A verificação passou a ser executada no GitHub Actions antes de preparar a assinatura.
- Versão atualizada de `1.4.33+43` para `1.4.34+44` com documentação e Novidades sincronizadas.

## Limitações

- Testes locais não substituem a compilação completa Gradle nem testes em aparelho Android.
## Verificações executadas na correção

- `python3 scripts/check_protocol_sources.py`: **aprovado**, 15 fontes Kotlin nas listas Gradle e shell.
- `python3 scripts/check_version.py`: **aprovado**, versão 1.4.34+44.
- `python3 scripts/check_android_resources.py`: **aprovado**, 342 strings verificadas.
- `python3 scripts/check_build_config.py`: **aprovado**, ferramentas e níveis SDK consistentes.
- `bash scripts/test_protocol.sh`: **aprovado**, compilação Kotlin/JVM e testes de protocolo, H.264 Annex-B, autenticação e ONVIF.
- `pytest -q tests`: **68 testes aprovados e 2 subtestes**.
- YAML do workflow, XMLs e integridade do ZIP serão inspecionados antes da entrega.

## Limitação de ambiente

O executável Gradle e o Android SDK não estão disponíveis neste ambiente. O resultado esperado é a correção da falha observada em `:compileProtocolTests`, mas a compilação completa `assembleRelease` só será confirmada quando o GitHub Actions executar a 1.4.34.


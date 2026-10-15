# Validação local 1.4.16+26

Origem: erro `O Secret não contém apenas caracteres Base64 válidos` no Build APK 18, etapa de preparo de assinatura, antes do Gradle.

Nova estratégia: importador privado `KEYSTORE_HEX` com certificado original; Base64 legado opcional. Testar conversão HEX/PKCS12 e comparação SHA-256 com chave em ambiente local seguro. O arquivo confidencial **não** é incluído neste projeto.

Os testes locais de sintaxe/integração não substituem um build Gradle completo nem testes físicos em Android. Confirmar a etapa de preparo e a compilação via GitHub Actions após importar os Secrets.

## Resultados da rodada local

- `check_version.py` e `check_build_config.py`: aprovados.
- `unittest test_signing_preflight.py`: 16 testes aprovados.
- Pré-processamento com o PKCS12 original: HEX aprovado mesmo com Base64 inválido no ambiente, bytes da chave idênticos e certificado SHA-256 igual ao fixado no projeto. Base64 legado válido aprovado.
- `test_qr_integration.py`, `test_preview_integration.py`, `test_ui_1414.py`: aprovados.
- XML: 47 arquivos interpretados sem erro; workflow YAML interpretado com sucesso.
- `scripts/test_protocol.sh`: execução excedeu o limite do ambiente e terminou sem resultado final, **não aprovada nesta rodada**. Código Kotlin de protocolo não foi alterado.
- Compilação Android/Gradle completa: indisponível neste ambiente, ainda pendente no GitHub Actions.

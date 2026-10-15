# Validação 1.4.15+25: falha de assinatura no Build APK 17

## Evidências do ZIP de logs

- `build/7_Verificar versionamento e compatibilidade do build.txt` aprovou versionamento **1.4.13+23** e configurações.
- `build/8_Preparar assinatura Release permanente.txt` falhou com `base64: invalid input` antes do Gradle. As quatro variáveis existiam, porém o conteúdo exato é ocultado pelo GitHub.
- O `.txt` original e o `.p12` guardados como backup local foram conferidos: o Base64 possui 4764 caracteres, decodifica em 3572 bytes e corresponde byte a byte ao PKCS12 existente.

## Correções e validações locais

- [x] Novo decodificador seguro em `scripts/prepare_signing.py`, testado com original, Base64 sem padding, com whitespace/aspas e dados deliberadamente inválidos.
- [x] Confirmado o certificado SHA-256 real com `keytool` sem revelar senhas.
- [x] Mensagem instrutiva em caso de valor corrompido, sem logar segredos.
- [x] Testes unitários de regressão executáveis em `tests/test_signing_preflight.py` e integrados ao workflow.
- [ ] Confirmar quais bytes estão armazenados em `KEYSTORE_BASE64` no GitHub: **não é possível ler o valor do Secret pela API**. Reimportar o JSON original para substituir se o build continuar falhando.
- [ ] Executar GitHub Actions com os Secrets corrigidos para confirmar o Gradle e o APK assinado.
- [ ] Testar atualização no Android com assinatura igual à do aplicativo instalado.

A chave privada/Secrets não foram adicionados ao código-fonte. A assinatura pública fixada em `docs/SIGNING_CERT_SHA256.txt` é a mesma da 1.4.13.

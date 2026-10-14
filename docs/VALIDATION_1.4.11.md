# Validação da v1.4.11+21 (pareamento QR)

Data: 2026-10-14.

## Verificações realizadas

- `python3 scripts/check_version.py`: aprovado, metadados `1.4.11+21` sincronizados.
- `python3 scripts/check_build_config.py`: aprovado, preservados compileSdk 36, targetSdk 34, AGP, Gradle, Kotlin, LibVLC e splits.
- `python3 tests/test_qr_integration.py`: aprovado, confere o diálogo com papéis, geração/escaneamento QR, validação HTTP local, recursos XML, permissão câmera, manifesta aceleração gráfica e uso do viewer existente.
- Parse de XML do manifesto, recursos e layouts: aprovado.
- `bash scripts/test_protocol.sh` passou integralmente antes de ampliar os casos negativos de URL QR. A reexecução posterior atingiu o limite de execução do ambiente e, por isso, **os casos novos não foram confirmados na suíte completa**; a função de validação das URLs não foi alterada.

## Testes ainda necessários

- `gradle testProtocol testReleaseUnitTest assembleRelease --no-daemon --build-cache` no CI (não há Gradle/Android SDK completo neste ambiente).
- Testar ZXing em dois Androids, solicitação de permissão CAMERA no receptor, leitura de QR real e resolução da dependência `com.journeyapps:zxing-android-embedded:4.3.0`.
- Testar rede Wi-Fi sem internet, hotspot, cliente isolado e mudança de IP; token revogado; pausa ao bloquear a tela; rotação.
- Verificar assinatura da versão instalada antes de tentar atualizar sem perder dados.

## Segurança

O QR contém uma URL com segredo de acesso; não é chave de pareamento criptográfico nem estabelece canal protegido. O receptor aceita apenas HTTP IPv4 privado com porta explícita e rota `/live/` com token hexadecimal (32 caracteres). Streaming permanece HTTP não criptografado. Não compartilhar QR com desconhecidos.

Nenhum APK foi gerado nem testado fisicamente nesta análise. O projeto Vigia IA não foi alterado.

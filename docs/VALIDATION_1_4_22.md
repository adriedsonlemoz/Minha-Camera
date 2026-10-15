# Build APK 23: correção v1.4.22+32

## Evidência

Build APK 23 (v1.4.21+31): `:compileProtocolTests FAILED` com `unresolved reference 'PhoneBatteryReading'` em `PhoneCameraServer.kt`, gerando erros em cascata nos campos de telemetria. Assinatura e configuração anteriores passaram. A compilação `:app:assembleRelease` não chegou a ser executada.

## Causa

`PhoneBatteryReading` estava declarado no arquivo Android `PhoneBatteryOverlay.kt`, mas a suíte de protocolos JVM compila apenas uma lista selecionada de arquivos. O servidor MJPEG passou a depender desse tipo sem que a lista de fontes fosse atualizada.

## Correção

- Criado `PhoneBatteryReading.kt`, sem imports Android.
- Incluído nas listas de fontes de `build.gradle.kts` e `scripts/test_protocol.sh`.
- `ProtocolSmoke.kt` verifica valores de telemetria e cabeçalhos X-Battery-* em stream simulado.
- O método `PhoneCameraServer.publish` passou a ser `internal` para não expor um parâmetro de tipo `internal` em API pública.

## Limitações

A correção da compilação JVM não prova sucesso de build Android completo nem funcionamento em aparelhos reais. Testar a compilação Release no GitHub Actions e a transmissão em dois celulares.

## Resultados locais realizados

- `scripts/check_version.py`: aprovado, 1.4.22+32.
- `scripts/check_build_config.py`: aprovado, mantendo ferramentas e assinatura.
- `kotlinc` isolado com stubs do relógio Android compilou `PhoneBatteryReading.kt` e `PhoneCameraServer.kt`.
- Teste de servidor local em loopback: publicado JPEG e recebidos `X-Battery-Level`, `X-Battery-Temp-Tenths`, `X-Battery-Charging` e `X-Battery-Average-Ma` com conteúdo binário preservado.
- `tests/test_battery_overlay_1418.py`: 11 verificações JVM mais invariantes estáticas, aprovado.
- `scripts/test_protocol.sh`: execução integral excedeu limite do ambiente, portanto **não considerada aprovada**.
- Build completo Android Gradle e APK assinado: **não realizados**; confirmar no próximo GitHub Actions.

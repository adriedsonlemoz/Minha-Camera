# Validação da versão 1.4.58+68 (2026-10-16)

## Alterações

- Painel de ações normais da câmera transmitindo movido para depois de `phoneVideoBox`, eliminando sobreposição com a TextureView.
- Tela cheia reserva 58 dp no root para barra opaca de controles, usando a mesma `TextureView` já conectada, sem reinicialização intencional ao girar.
- Zoom da câmera via `Camera.Parameters` e posto no main thread; se o telefone não reportar `isZoomSupported`, a interface remota desabilita.
- Controle remoto por POST e token de 32 hex, com faixa 0..100, atendido pela Activity MJPEG e pelo serviço MJPEG com tela apagada.

## Testes locais

- `python -m unittest discover -s tests -p test*.py -q`: 122 testes aprovados.
- `scripts/test_protocol.sh`: aprovado em todas as etapas, inclusive protocolos HTTP autenticados de zoom com token inválido e valores fora da faixa.
- Verificadores de recursos Android, imports, Kotlin strings, bindings e protocolos: aprovados.
- ZIP: validar CRC, nomes e versão no empacotamento.

## Não executado

- `:app:compileReleaseKotlin`, `:app:assembleRelease` e verificação APK por Gradle, pois Gradle/SDK Android não estão disponíveis no ambiente.
- Teste físico: Redmi A3 transmissor, receptor, zoom e rotação com tela apagada.
- Zoom por H.264 e câmera dupla não implementado nesta release; UI exibe indisponível.

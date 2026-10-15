# Validação 1.4.18+28

Validação local de código-fonte; não equivale a APK instalado.

- `tests/test_battery_overlay_1418.py`: casos de percentuais, temperatura ausente, formatação pt-BR e integração da sobreposição antes dos destinos MJPEG/MP4.
- `scripts/check_version.py`, `scripts/check_build_config.py`, parser XML e PSI Kotlin.
- Preservada assinatura constante; nenhuma chave privada ou Secrets incluídos no arquivo entregue.
- Pendentes: `gradle testProtocol testReleaseUnitTest assembleRelease --build-cache` no CI, verificação de gravação MP4, câmera dupla física, custo de CPU/latência do selo e exibição em dois aparelhos.
- Limite: o sistema pode reportar dados atrasados/inválidos; não confundir temperatura da bateria com CPU.

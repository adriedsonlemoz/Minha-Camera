# Validação do Minha Câmera 1.4.27+37 (2026-10-15)

## Executado neste ambiente

- `python3 scripts/check_version.py`: 1.4.27+37 sincronizado entre Gradle, AGENTS, README, CHANGELOG, VERSIONING, RELEASE_NOTES e tela Novidades.
- `python3 scripts/check_android_resources.py`: 256 strings, sem apóstrofos ou escapes inválidos.
- `python3 -m unittest discover -s tests -p test_*.py`: testes de integração, regressão e assinatura aprovados, incluindo novo FGS e controle remoto.
- `bash scripts/test_protocol.sh`: Kotlin/JVM com stubs, incluindo POST autenticado para iniciar/parar REC e rejeição de token incorreto, além de ONVIF/RTSP/MJPEG existentes; aprovado.
- Kotlin PSI (`PsiCheck.kt`) analisa todos os 31 arquivos Kotlin do aplicativo, sem erros de sintaxe.
- ZIP: verificar integridade CRC e estrutura ao empacotar.

## Ainda não executado

- Gradle/Android: comando `gradle testProtocol testReleaseUnitTest assembleRelease --build-cache` indisponível porque `gradle` não existe neste ambiente; não há Android SDK instalado nem chave PKCS12 local da assinatura estável.
- Teste instrumentado em Redmi A3: bloquear a tela por 30, 60 e 120 minutos, conferir continuidade de MJPEG/REC, liberação ao parar, pasta Downloads/MinhaCamera, reconexão, rotação, latência, wake lock, consumo e aquecimento.
- Verificar funcionamento da SurfaceTexture headless em Redmi A3/MIUI: HALs de câmera podem interromper envio quando a tela apaga ou a fila de SurfaceTexture não é consumida. Não assumir compatibilidade sem medição no hardware.
- Iniciar FGS com a Activity visível em Android 14+; nunca tentar recriar o serviço de câmera automaticamente após encerramento em segundo plano. Testar permissão de notificação no Android 13+.
- Conferir endpoint POST remoto somente em rede privada confiável. O link/token percorre HTTP sem criptografia.

**Aviso:** código-fonte atualizado e testes locais não significam que um APK tenha sido compilado nem que o modo com tela apagada tenha sido validado fisicamente.

# Minha Câmera 1.4.4 (versionCode 14)

**Data:** 2026-10-11

## Falha corrigida (GitHub Actions: Build APK #7)

O workflow falhou na tarefa `:app:checkReleaseAarMetadata`: `org.videolan.android:libvlc-all:3.7.7` exige **Android API 36**, mas a versão 1.4.3 compilava com API 34 e AGP 8.5.2.

### Alterações

- `compileSdk = 36`, mantendo `minSdk = 24` e `targetSdk = 34`.
- Android Gradle Plugin **8.11.1**, Gradle **8.13**, Kotlin Gradle Plugin **2.2.20** e Java **17**.
- Workflow GitHub Actions instala a plataforma **android-36** e as Build Tools **35.0.0**.
- Regex que mascara URLs RTSP nos detalhes de erro corrigida para evitar uma falha de sintaxe Kotlin após a etapa de metadados.
- Adicionado o teste preventivo `python3 scripts/check_build_config.py` ao pipeline.

### Verificações realizadas

- Verificação de versionamento e configuração de build.
- Testes de protocolo simulados ONVIF, RTSP, autenticação e SDP.
- Validação sintática dos XMLs e do workflow.

**Não foi executado o build completo do APK neste ambiente.** A correção remove a incompatibilidade demonstrada pelo log, mas requer nova execução do GitHub Actions para confirmar a compilação de todas as etapas.

### Próxima validação no GitHub

1. Envie os arquivos desta versão ao repositório com a estrutura original.
2. Inicie **Actions > Build APK > Run workflow** (ou faça push na branch principal).
3. Confirme que `:app:checkReleaseAarMetadata` passa e `assembleRelease` termina sem erros.
4. Teste a instalação, a descoberta de câmeras e a reprodução H.264/H.265 no dispositivo.

**Assinatura:** o workflow continua usando assinatura debug. Os Secrets de assinatura estável mencionados anteriormente só serão usados se o Gradle/workflow forem configurados explicitamente para isso. Uma chave diferente não atualiza um APK previamente instalado sem desinstalação.

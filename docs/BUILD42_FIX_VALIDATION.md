# Validação da correção do Build APK 42 | Minha Câmera 1.4.42+52

## Evidência do log recebido

- Workflow: **Build APK 42**.
- Tarefa que falhou: `:app:processReleaseResources`.
- AAPT2 informou **16 erros** em `app/src/main/res/layout/item_camera.xml` porque os quatro botões usavam `app:insetLeft`, `app:insetRight`, `app:insetTop` e `app:insetBottom`, não encontrados pelo linkador de recursos.
- Os passos de preparação da assinatura e de verificação de configuração haviam avançado antes dessa falha. A compilação Kotlin do APK ainda não tinha sido concluída.

## Mudança

- Removidas 16 referências `app:inset*`, sem trocar os widgets `MaterialButton`, IDs `btnOpen`, `btnCopy`, `btnEdit` e `btnDelete`, alturas 48dp, pesos do layout nem handlers de cliques existentes.
- `scripts/check_android_resources.py` passa a analisar também os arquivos `res/layout*/*.xml` e rejeitar esse caso antes do Gradle; `tests/test_android_resources.py` possui testes de regressão.
- O preflight novo foi executado também contra o código da versão 1.4.41: retornou 16 falhas. Na 1.4.42 retornou zero falhas.

## Verificações locais realizadas

- `python scripts/check_version.py`: OK, `1.4.42+52`.
- `python scripts/check_android_resources.py`: OK, 358 strings, zero atributos incompatíveis.
- `python scripts/check_build_config.py`: OK, SDK e plugins coerentes.
- `python scripts/check_protocol_sources.py`: OK, 16 fontes sincronizadas.
- `python scripts/check_kotlin_imports.py`: OK, 55 arquivos.
- `python -m pytest -q tests`: **91 testes aprovados + 2 subtestes**.
- `bash scripts/test_protocol.sh`: testes Kotlin/JVM simulados de MJPEG, H.264, ONVIF, RTSP, MCRP, orientação, Eco, controle remoto e gravações aprovados; avisos não bloqueantes de API `URL` deprecated.
- XML dos recursos Android: **71 arquivos** bem formados.

## Ainda pendente

- O ambiente local não disponibiliza Gradle/Android SDK e não permite confirmar `:app:processReleaseResources` nem `assembleRelease` de verdade.
- Executar o workflow do GitHub Actions para gerar os cinco APKs Release assinados com a **mesma keystore estável** configurada no repositório. Validar `scripts/verify_apks.py` após o build.
- Testar no Android os cartões de ações em tela estreita e os modos de orientação do transmissor/receptor, inclusive durante REC e com a tela apagada.

Esta correção é restrita à camada de layout e a verificações de build; não altera codecs, rede, permissões, protocolos, certificados nem o projeto Vigia IA.

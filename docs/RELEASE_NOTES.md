# Minha Câmera 1.4.5 (versionCode 15)

**Data:** 2026-10-11

## Problema corrigido no build GitHub Actions #8

O job terminou com **exit code 127**, pois `sdkmanager` não estava disponível no `PATH` do runner Ubuntu. O erro ocorreu no passo "Instalar Android SDK 36 e Build Tools", antes da inicialização do Gradle.

## Mudanças aplicadas

- Adicionado `android-actions/setup-android@v4` ANTES de executar `sdkmanager`. A ação configura o diretório SDK, fornece as ferramentas de linha de comando e aceita as licenças.
- Removido o comando antigo `yes | sdkmanager --licenses > /dev/null || true`, que escondia falhas e não preparava o executável.
- A instalação de `platforms;android-36` e `build-tools;35.0.0` agora verifica `command -v sdkmanager`, a versão do utilitário e a existência do `android.jar` após instalar.
- Modernizadas as ações `actions/checkout@v7` e `actions/setup-java@v6`, usando JDK 17 como antes.
- `scripts/check_build_config.py` agora verifica a presença e a ordem da preparação do Android SDK no workflow.
- Versionamento sincronizado para **1.4.5+15**.

## Validação

- Testes simulados de rede/protocolo e verificações de versionamento e configuração de build: executar `bash scripts/test_protocol.sh`, `python3 scripts/check_version.py` e `python3 scripts/check_build_config.py`.
- **APK não compilado neste ambiente**. A correção específica do build #8 ainda deve ser confirmada numa nova execução de Actions.

## Próximo teste

1. Atualize o código da branch principal com a versão 1.4.5.
2. Execute **Actions → Build APK → Run workflow**.
3. Confirme a execução da etapa "Preparar Android SDK" e a instalação `platforms;android-36`.
4. Confira a etapa `gradle assembleRelease` e, se houver nova falha, guarde seu log para análise.
5. Se gerar o APK, confirme assinatura, instalação e reprodução das câmeras no telefone.

**Nota:** o APK release ainda usa chave de debug gerada no runner; futuras atualizações exigem assinatura estável para preservar os dados do aplicativo. Não foram adicionados Secrets de assinatura nesta correção.

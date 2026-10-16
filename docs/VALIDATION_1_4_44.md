# Validação da versão 1.4.44+54

Data: 2026-10-15

## Alterações validadas
- Correção da orientação fixa do transmissor para evitar imagem de cabeça para baixo no lado oposto da horizontal/vertical.
- Modos fixos agora escolhem automaticamente o lado físico correto (0/180 ou 90/270).
- Sensor de orientação mantido ativo também nos modos fixos, na Activity e no serviço em segundo plano.
- Sincronização de versionName/versionCode, README, CHANGELOG, VERSIONING, RELEASE_NOTES e Novidades.

## Verificações executadas
- `python3 scripts/check_version.py` → **OK** (`1.4.44+54` sincronizado).
- `python3 scripts/check_build_config.py` → **OK**.
- `python3 scripts/check_android_resources.py` → **OK**.
- `python3 scripts/check_kotlin_imports.py` → **OK**.
- Parse de XML em `app/src/main/res/**/*.xml` → **OK** (71 arquivos).

## Pendências
- Não foi possível executar build Gradle/Android local porque o pacote entregue não inclui `gradlew`.
- APK/CI e teste em aparelho ainda pendentes para confirmar em hardware real a orientação fixa em vertical e horizontal, inclusive no serviço em segundo plano.

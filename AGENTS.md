# Orientações para manutenção

- Usar versionamento sequencial a cada correção: aumentar `versionName` e `versionCode` (+1).
- Sincronizar `app/build.gradle.kts`, `README.md`, `CHANGELOG.md`, `docs/VERSIONING.md`, `docs/RELEASE_NOTES.md` e a tela "Novidades e versões" (`strings.xml`).
- Não afirmar que houve testes em dispositivo ou build APK quando somente houve análise estática.
- Preservar a capacidade de adicionar câmeras manualmente e câmeras anteriormente salvas.
- Não usar credenciais reais em logs, testes ou repositório.
- Documentar os resultados úteis para migração futura ao Vigia IA, sem modificar esse segundo aplicativo automaticamente.

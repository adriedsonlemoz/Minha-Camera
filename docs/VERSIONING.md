# Política de versionamento | Minha Câmera

O projeto adota a disciplina de releases sequenciais do GitHub Manager:

1. Para cada alteração entregue, incrementar `versionName` e `versionCode` em `app/build.gradle.kts`.
2. Incrementar `versionCode` **sempre em 1**, sem reutilizar códigos publicados.
3. A sequência recebida foi `1.4.0+10` → `1.4.1+11` → `1.4.2+12` → `1.4.3+13`; a próxima alteração deve ser `1.4.4+14`.
4. Sincronizar `README.md`, `CHANGELOG.md`, este documento, `docs/RELEASE_NOTES.md` e o histórico visível no menu "Novidades e versões" (`strings.xml`).
5. Conferir as verificações estáticas e o build do GitHub Actions antes de publicar a release.
6. Manter as anotações de incompatibilidades, correções pendentes e limitações para o Vigia IA.

Versão atual: **1.4.3+13**, em 2026-10-11.

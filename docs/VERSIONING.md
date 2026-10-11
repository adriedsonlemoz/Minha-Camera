# Política de versionamento | Minha Câmera

O projeto adota a disciplina de releases sequenciais do GitHub Manager:

1. Para cada alteração entregue, incrementar `versionName` e `versionCode` em `app/build.gradle.kts`.
2. Incrementar `versionCode` **sempre em 1**, sem reutilizar códigos publicados.
3. A sequência recebida foi `1.4.0+10` → `1.4.1+11` → `1.4.2+12` → `1.4.3+13` → `1.4.4+14` → `1.4.5+15` → `1.4.6+16` → `1.4.7+17`; a próxima alteração deve ser `1.4.8+18`.
4. Sincronizar `README.md`, `CHANGELOG.md`, este documento, `docs/RELEASE_NOTES.md` e o histórico visível no menu "Novidades e versões" (`strings.xml`).
5. Conferir as verificações estáticas e o build do GitHub Actions antes de publicar a release.
6. Manter as anotações de incompatibilidades, correções pendentes e limitações para o Vigia IA.

Versão atual: **1.4.7+17**, em 2026-10-11.

## APKs por ABI

Universal, armeabi-v7a, arm64-v8a, x86_64 e x86 usam `com.minhacamera`, versionName 1.4.7 e versionCode 17, sem offsets. Para instalação direta, o Android não exige códigos diferentes por ABI. A próxima versão usa 18 em todos. A troca entre Universal e ABI na mesma versão é uma reinstalação com o mesmo código; requer a mesma assinatura e uma ABI suportada pelo sistema Android (um processador de 64 bits pode usar Android de 32 bits).

A chave debug do ambiente continua configurada. Mesma assinatura entre os cinco APKs de uma compilação não prova continuidade com releases anteriores. Para preservar atualizações legadas, é necessário recuperar a chave privada original e comparar o SHA-256 do certificado com o APK instalado. Um certificado ou APK sozinho não permite reconstruir a chave. Não criar uma chave substituta e apresentá-la como compatível.

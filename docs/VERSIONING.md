# Política de versionamento | Minha Câmera

O projeto adota a disciplina de releases sequenciais do GitHub Manager:

1. Para cada alteração entregue, incrementar `versionName` e `versionCode` em `app/build.gradle.kts`.
2. Incrementar `versionCode` **sempre em 1**, sem reutilizar códigos publicados.
3. A sequência recebida foi `1.4.0+10` → `1.4.1+11` → `1.4.2+12` → `1.4.3+13` → `1.4.4+14` → `1.4.5+15` → `1.4.6+16` → `1.4.7+17` → `1.4.8+18` → `1.4.9+19` → `1.4.10+20` → `1.4.11+21` → `1.4.12+22` → `1.4.13+23` → `1.4.14+24` → `1.4.15+25` → `1.4.16+26` → `1.4.17+27`; a próxima alteração deve ser `1.4.18+28`.
4. Sincronizar `README.md`, `CHANGELOG.md`, este documento, `docs/RELEASE_NOTES.md` e o histórico visível no menu "Novidades e versões" (`strings.xml`).
5. Conferir as verificações estáticas e o build do GitHub Actions antes de publicar a release.
6. Manter as anotações de incompatibilidades, correções pendentes e limitações para o Vigia IA.

Versão atual: **1.4.17+27**, em 2026-10-15.

## APKs por ABI

Universal, armeabi-v7a, arm64-v8a, x86_64 e x86 usam `com.minhacamera`, versionName 1.4.17 e versionCode 27, sem offsets. Para instalação direta, o Android não exige códigos diferentes por ABI. A próxima versão usará 28 em todos. A troca entre Universal e ABI na mesma versão é uma reinstalação com o mesmo código; requer a mesma assinatura e uma ABI suportada pelo sistema Android (um processador de 64 bits pode usar Android de 32 bits).

A partir de 1.4.13, assinatura Release sempre via Secrets e keystore PKCS12 fixa. O workflow rejeita assinatura divergente de docs/SIGNING_CERT_SHA256.txt. Essa chave NÃO corresponde necessariamente à instalação legada: APK com certificado diferente não atualiza sem desinstalar, mesmo com versionCode crescente. A chave usada na instalação anterior precisa ser recuperada para atualizar diretamente. Nunca trocar a chave desta linha de releases; manter backup da keystore privada e senhas fora do GitHub e sem compartilhamento público.

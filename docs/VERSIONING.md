# Política de versionamento | Minha Câmera

O projeto adota a disciplina de releases sequenciais do GitHub Manager:

1. Para cada alteração entregue, incrementar `versionName` e `versionCode` em `app/build.gradle.kts`.
2. Incrementar `versionCode` **sempre em 1**, sem reutilizar códigos publicados.
3. A sequência recebida foi `1.4.0+10` → `1.4.1+11` → `1.4.2+12` → `1.4.3+13` → `1.4.4+14` → `1.4.5+15` → `1.4.6+16` → `1.4.7+17` → `1.4.8+18` → `1.4.9+19` → `1.4.10+20` → `1.4.11+21` → `1.4.12+22` → `1.4.13+23` → `1.4.14+24` → `1.4.15+25` → `1.4.16+26` → `1.4.17+27` → `1.4.18+28` → `1.4.19+29` → `1.4.20+30` → `1.4.21+31` → `1.4.22+32` → `1.4.23+33` → `1.4.24+34` → `1.4.25+35` → `1.4.26+36` → `1.4.27+37` → `1.4.28+38` → `1.4.29+39` → `1.4.30+40` → `1.4.31+41` → `1.4.32+42` → `1.4.33+43` → `1.4.34+44` → `1.4.35+45` → `1.4.36+46` → `1.4.37+47` → `1.4.38+48` → `1.4.39+49` → `1.4.40+50` → `1.4.41+51` → `1.4.42+52` → `1.4.43+53` → `1.4.44+54` → `1.4.45+55` → `1.4.46+56` → `1.4.47+57` → `1.4.48+58` → `1.4.49+59` → `1.4.50+60` → `1.4.51+61` → `1.4.52+62` → `1.4.53+63` → `1.4.54+64` → `1.4.55+65` → `1.4.56+66` → `1.4.57+67` → `1.4.58+68` → `1.4.59+69` → `1.4.60+70` → `1.4.61+71` → `1.4.62+72`; a próxima alteração deve ser `1.4.63+73`.
4. Sincronizar `README.md`, `CHANGELOG.md`, este documento, `docs/RELEASE_NOTES.md` e o histórico visível no menu "Novidades e versões" (`strings.xml`).
5. Conferir as verificações estáticas e o build do GitHub Actions antes de publicar a release.
6. Manter as anotações de incompatibilidades, correções pendentes e limitações para o Vigia IA.

Versão atual: **1.4.62+72**, em 2026-10-16.

## APKs por ABI

Universal, armeabi-v7a, arm64-v8a, x86_64 e x86 usam `com.minhacamera`, versionName 1.4.62 e versionCode 72, sem offsets. Para instalação direta, o Android não exige códigos diferentes por ABI. A próxima versão usará 73 em todos. A troca entre Universal e ABI na mesma versão é uma reinstalação com o mesmo código; requer a mesma assinatura e uma ABI suportada pelo sistema Android (um processador de 64 bits pode usar Android de 32 bits).

A partir de 1.4.13, assinatura Release sempre via Secrets e keystore PKCS12 fixa. O workflow rejeita assinatura divergente de docs/SIGNING_CERT_SHA256.txt. Essa chave NÃO corresponde necessariamente à instalação legada: APK com certificado diferente não atualiza sem desinstalar, mesmo com versionCode crescente. A chave usada na instalação anterior precisa ser recuperada para atualizar diretamente. Nunca trocar a chave desta linha de releases; manter backup da keystore privada e senhas fora do GitHub e sem compartilhamento público.

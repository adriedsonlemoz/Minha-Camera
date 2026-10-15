# Orientações para manutenção

- Usar versionamento sequencial a cada correção: aumentar `versionName` e `versionCode` (+1).
- Sincronizar `app/build.gradle.kts`, `README.md`, `CHANGELOG.md`, `docs/VERSIONING.md`, `docs/RELEASE_NOTES.md` e a tela "Novidades e versões" (`strings.xml`).
- Não afirmar que houve testes em dispositivo ou build APK quando somente houve análise estática.
- Preservar a capacidade de adicionar câmeras manualmente e câmeras anteriormente salvas.
- Não usar credenciais reais em logs, testes ou repositório.
- Documentar os resultados úteis para migração futura ao Vigia IA, sem modificar esse segundo aplicativo automaticamente.

- Versão atual: 1.4.37+47. APKs diretos compartilham versionCode 46; não aplicar offsets por ABI.
- Manter Universal e splits das quatro ABIs fornecidas pelo LibVLC; verificar o AAR e os APKs.
- Executar `gradle testProtocol testReleaseUnitTest assembleRelease --build-cache` uma única vez; depois `scripts/verify_apks.py`.
- Assinar TODO release com a keystore estável dos Secrets KEYSTORE_HEX (preferido) ou KEYSTORE_BASE64 (legado), com KEYSTORE_PASSWORD, KEY_ALIAS e KEY_PASSWORD.
- Nunca voltar a assinar Release com Debug, criar chave nova em cada build ou publicar se faltar qualquer Secret.
- Conferir o certificado contra docs/SIGNING_CERT_SHA256.txt antes de assinar/publicar.
- Keystore, Secrets e senhas ficam fora do ZIP do projeto e do GitHub; proteger backups offline.
- A nova chave NÃO atualiza instalações assinadas por outra chave. Recuperar a chave legada se for indispensável manter a instalação anterior.

- Gravação local usa o mesmo player LibVLC e termina ao sair; nunca afirmar gravação contínua em background ou acesso a arquivos históricos da câmera.
- Nomes RTSP produzidos pelo VLC ficam em pasta privada de sessão e devem ser renomeados antes de compartilhar.

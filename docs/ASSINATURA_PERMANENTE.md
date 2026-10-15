# Assinatura permanente do Minha Câmera (1.4.13+23)

## Preparação e importação

Foram gerados três arquivos PRIVADOS **fora** do código-fonte: uma chave `Minha-Camera-CHAVE-ASSINATURA-PRIVADA.p12` e importadores `Minha-Camera-Secrets-IMPORTAR-GitHub-Manager.txt` / `.json`. Os importadores têm os quatro Secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

No GitHub Manager: Repositório **Minha Câmera > Secrets > Importar** > selecione o `.txt` (UTF-8) > confira os quatro nomes > confirme criar/substituir. O workflow do repositório deve ser atualizado para a versão atual antes de compilar. No GitHub, o mesmo pode ser feito em Settings > Secrets and variables > Actions.

Os Secrets devem ser *repository secrets* acessíveis ao workflow. Não publicar a keystore ou importadores, não incluí-los no ZIP do projeto e não enviá-los ao GitHub como arquivos versionados. `KEYSTORE_BASE64` é base64 de uma keystore binária, **não criptografia**. A keystore PKCS12 é protegida por senha; o arquivo de importação contém a senha.

## Certificado fixado

- Formato da keystore: PKCS12 (RSA 3072).
- Alias: `minha_camera_release`.
- Certificado SHA-256: `1d6eb49ce338083bf19f67a22fad60b6122d7ea23b7c5cf363c9f7c14da44e46`.
- Fixado em `docs/SIGNING_CERT_SHA256.txt` e verificado antes do Gradle e nos APKs finais.
- Não mudar a keystore nem os quatro Secrets em lançamentos futuros sem motivo explícito e plano de migração.

## Atualização sem desinstalar

Para instalar um APK novo por cima de um antigo, Android normalmente exige o mesmo `applicationId`, a mesma assinatura (certificado/chave compatível) e `versionCode` apropriado. A versão 1.4.13 usa chave **nova**, sem comprovação de coincidência com os APKs já instalados. Os builds 1.4.12 usavam a chave Debug de seu runner; essa chave pode variar entre execuções. **A criação de novos Secrets NÃO resolve a atualização de instalações assinadas anteriormente com outra chave.**

Para comparar: obtenha o APK antigo sem desinstalar o aplicativo e execute `apksigner verify --print-certs APK_ANTIGO.apk`. Confira `Signer #1 certificate SHA-256 digest` com o valor acima. Se for diferente, não há atualização direta normal com esta chave. Se houver um backup da *keystore original* do APK antigo, poderá assinar com ela após configurar os Secrets correspondentes; apenas o APK e o certificado não permitem recuperar a chave privada. Se a troca de assinatura for inevitável, salve previamente os dados disponíveis, sabendo que o app pode não oferecer migração completa de credenciais/gravações.

## Operação segura

- Configurar os quatro Secrets; não copiar o `.txt` para o código do repositório.
- Fazer backup offline de `.p12` **e** das senhas. Sem ambos não será possível reproduzir esta assinatura no futuro.
- O workflow falha antes do build se faltar qualquer Secret ou se o certificado não coincidir.
- Os cinco APKs gerados numa execução devem apresentar a mesma assinatura. O `scripts/verify_apks.py` compara a assinatura com a fixada e bloqueia a publicação se divergir.
- Arquivos da chave são excluídos ao fim da execução do runner. Não colocar senhas/keystore em logs.
- Para atualizar o app instalado, prefira gerar o APK com `versionCode` maior, preservando `applicationId` e a assinatura.

## Validação pendente

`keytool` gerou a keystore, validou seu alias e exportou o certificado em formato DER. O fingerprint gravado foi calculado por SHA-256 dos bytes DER, igual ao algoritmo de digest do certificado. Testes estáticos e parsing YAML/XML passam localmente; execução real do GitHub Actions, `assembleRelease`, assinatura com `apksigner` e teste de atualização no Android ainda precisam ser executados.

## Build APK 17: Base64 inválido e solução (1.4.15+25)

A etapa **Preparar assinatura Release permanente** de uma execução da versão **1.4.13+23** encerrou com `base64: invalid input`, antes da compilação Android. Todos os quatro Secrets tinham valores não vazios, mas o `KEYSTORE_BASE64` fornecido ao runner não era um Base64 aceito pelo decodificador GNU. O importador original TXT/JSON foi conferido localmente e gera exatamente a keystore de backup, portanto o valor salvo no repositório precisa ser revisado. Os logs não revelam qual caractere foi alterado ou removido.

1. Atualize o código/workflow para **1.4.15+25**. Esta versão aceita quebras de linha, espaços, aspas externas e padding `=` omitido no Base64 e valida o PKCS12 e o certificado antes do Gradle.
2. No GitHub Manager: **Repositório > Secrets > Importar** e selecione o **arquivo JSON ORIGINAL já guardado de forma privada** (ou o TXT original) e use **Substituir** para os quatro nomes. Isto mantém a mesma chave e o mesmo certificado. Não cole as linhas do `.env` como valor de um Secret individual.
3. Rode manualmente **Build APK**. Procure `Assinatura verificada: Base64, keystore PKCS12 e certificado SHA-256 conferidos.` antes da etapa Gradle. Se ainda falhar, reporte a mensagem nova, sem expor o Base64/credenciais.

Um Secret genuinamente truncado **não pode ser reconstruído a partir dos logs**; a solução nesse caso exige restaurar o valor íntegro. Nunca contornar a verificação do certificado, nem gerar outra keystore só para fazer o workflow passar.

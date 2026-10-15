# Validação 1.4.13+23: assinatura estável

- Keystore PKCS12 RSA 3072 gerada com chave privada única e protegida por senha; exportação de certificado DER e fingerprint SHA-256 conferidos.
- Quatro Secrets de repositório no formato importável pelo GitHub Manager gerados fora do projeto.
- Gradle Release aponta para credenciais de ambiente, sem `signingConfigs.debug`.
- GitHub Actions recusa Secrets incompletos, certificado divergente e limpa o arquivo privado ao finalizar.
- Verificador de cinco APKs recusa assinatura divergente do certificado público fixado.
- Testes de versionamento, configuração, YAML, XML, arquivos ZIP e presença dos markers de assinatura serão executados localmente.
- Não foi possível executar Gradle/Android completo neste ambiente; nenhum teste de instalação ou atualização física foi realizado.
- A chave nova não é compatível com instalações antigas de certificado diferente.

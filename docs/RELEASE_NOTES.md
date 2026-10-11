# Minha Câmera 1.4.7 (versionCode 17)

**Data:** 2026-10-11

## APKs menores com os recursos preservados

ABI Splits com `isEnable = true` e `isUniversalApk = true`. O AAR LibVLC 3.7.7 fornece armeabi-v7a, arm64-v8a, x86_64 e x86. Mantemos x86 para não excluir instalações Intel de 32 bits que o Universal anterior atendia. Cada biblioteca nativa é mantida integralmente dentro de sua ABI.

Uma única execução Gradle roda a suíte simulada existente (`testProtocol`), testes unitários release (atualmente sem testes Android/JUnit cadastrados) e `assembleRelease`. Preserva setup-gradle e build cache, sem matriz de builds completos. Cinco uploads separados de APK com `archive: false` mantêm compatibilidade com o GitHub Manager.

Todos os APKs usam applicationId com.minhacamera, versão 1.4.7 e código 17. Não há códigos por ABI. A configuração de assinatura debug preexistente foi mantida; os certificados dos arquivos são comparados no verificador. Continuidade sobre instalações antigas depende da chave privada original, que não é distribuída pelo repositório. Não foram criados Secrets nem chaves substitutas.

## Verificação

`scripts/verify_apks.py` lê output-metadata.json, verifica CRC, ABIs exatas, três bibliotecas LibVLC por ABI, conteúdo nativo idêntico ao Universal, mesmo DEX/recursos, manifesto, certificado e zipalign; gera nomes finais, SHA-256 e tabela em MB decimais (bytes / 1.000.000). Redução = 100 × (1 − tamanho ABI / tamanho Universal).

Build local: **BUILD SUCCESSFUL in 2m26s**, seis testes de protocolo aprovados. ARM64: **62,69 MB (−71,18%)**; ARMv7: **47,85 MB (−78,00%)**; Universal: **217,55 MB**. Os APKs locais têm certificado diferente do APK anterior 1.4.6 do Actions e não o atualizam diretamente. Publicação remota bloqueada por 403 na conexão GitHub. Resultados reais: [BUILD_VALIDATION.md](BUILD_VALIDATION.md). Testes em câmeras/dispositivo Android permanecem necessários para confirmar reprodução e atualização legada; testes de protocolo não validam decodificação HEVC.

## Otimizações adicionais

Não ativar R8 ou resource shrinking sem validar JNI/reflexão do LibVLC e todos os caminhos do player. Não remover codecs/plugins nativos ou LibVLC. O Universal continua grande porque contém quatro cópias nativas. AAB pode entregar só a ABI pela loja, mas não substitui os APKs diretos do GitHub Manager. Compressão das bibliotecas nativas pode reduzir download, mas altera extração/espaço/tempo de instalação e exige medição antes de mudar packaging. Não alterar minSdk ou targetSdk para reduzir tamanho.

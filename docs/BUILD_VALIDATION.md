# Validação 1.4.7+17

**Data:** 2026-10-11. Base: branch main de adriedsonlemoz/Minha-Camera, commit `358ee8ca178332fecfc90fb0bb7c10ec09613d6a` (1.4.6+16). Foi usada a cópia clonada do repositório, e não o ZIP anexado 1.4.5.

## Build e testes executados

- JDK Temurin 17.0.20.1, Gradle 8.13, AGP 8.11.1, Kotlin 2.2.20, Android SDK 36, Build Tools 35.0.0.
- `gradle testProtocol testReleaseUnitTest assembleRelease --no-daemon --build-cache --console=plain --stacktrace`: **BUILD SUCCESSFUL in 2m 26s**, 49 tarefas executadas, em uma única chamada bem-sucedida para os cinco APKs. O ambiente precisou ser preparado com SDK/JDK completos e acesso ao proxy; isso não modifica o workflow do runner.
- `testProtocol`: seis casos aprovados: codec apenas de vídeo no SDP; DESCRIBE H.265; máscaras /24 e /23 e limitação /16; Content-Length UTF-8/fallback Digest; ProbeMatch/XAddrs/RelatesTo; endpoint ONVIF real e perfil H.264.
- `testReleaseUnitTest`: **NO-SOURCE**, não há testes JUnit/Android cadastrados. Não contar como testes unitários executados.
- Verificações `check_version.py`, `check_build_config.py`, sintaxe Python, XML de strings, YAML do workflow e `git diff --check`: aprovadas.
- O verificador também foi testado com entradas inválidas: APK v7a rotulado ARM64 e APK sem libvlcjni; ambos recusados antes de gerar/publicar dist.
- Nenhum arquivo `.kt` de produção foi alterado. Somente Novidades foi atualizado nos recursos visíveis do app.

## APKs reais gerados localmente

| APK | MB (1 MB = 1.000.000 bytes) | Redução vs. Universal |
|---|---:|---:|
| universal | 217.55 | 0.00% |
| arm64-v8a | 62.69 | 71.18% |
| armeabi-v7a | 47.85 | 78.00% |
| x86_64 | 70.91 | 67.40% |
| x86 | 58.25 | 73.22% |

A redução usa `100 × (1 − bytes ABI / bytes Universal)`. Todos usam applicationId **com.minhacamera**, versionName **1.4.7**, versionCode **17**, minSdk **24**. O Universal inclui as quatro ABIs. Cada variante específica contém somente sua ABI. Cada ABI contém libc++_shared.so, libvlc.so e libvlcjni.so.

O verificador confirmou CRC ZIP, ausência de entradas duplicadas, `zipalign -c 4`, `apksigner verify`, igualdade dos metadados, certificado comum e código/recursos idênticos entre variantes. Os hashes de cada `.so` correspondem à cópia da mesma ABI no Universal. SHA-256 individuais estão em dist/SHA256SUMS e no relatório publicado com os APKs.

LibVLC AAR realmente resolvido: SHA-256 `b48dab96e0e90e34cce3861963500c144a5aadb1b249d501d6a4b104d849e61b`; CRC aprovado. Bibliotecas nativas brutas: arm64-v8a 55.263.312 bytes, armeabi-v7a 40.443.948, x86_64 63.498.704, x86 50.847.580. Isso explica a maior parte do peso do Universal.

## Assinatura e atualização legada: impedimento confirmado

A configuração de assinatura debug foi preservada, sem adicionar Secrets ou chaves ao projeto. O Android Gradle Plugin usa/gera a chave debug do próprio ambiente; isso já era o comportamento da 1.4.6.

- Certificado comum dos cinco APKs locais: `11e8e795e173d92421e769b40d612bfa03ac362ebbe8b7eedb90e9745d9f1368`.
- Certificado do APK 1.4.6+16 obtido do Actions #10, execução `38113656822`: `bc1982b78bac49b1589a2b0b42b855f7813a7a4aa36b69f332410dec5230d535`.
- **Os certificados são diferentes. Estes APKs locais são binários de validação e não atualizam diretamente aquele APK anterior.** Para entregar atualizações compatíveis, é necessário recuperar a chave privada original e usá-la no build. Não é possível extraí-la do APK/certificado. Não foi substituída manualmente a configuração de assinatura e não foi exportada a chave debug do ambiente.
- O APK anterior tem 217.550.893 bytes; o Universal novo tem 217.551.121 bytes. O ganho vem de escolher o APK por ABI; o Universal mantém a cobertura anterior.

## GitHub: publicação impedida

A tentativa de criar a árvore de arquivos atualizados pela conexão GitHub falhou com **403 Resource not accessible by integration** no endpoint `POST /repos/adriedsonlemoz/Minha-Camera/git/trees`. A alternativa `git push --dry-run` não tem credencial disponível (`could not read Username ... terminal prompts disabled`). A integração está instalada, mas não permite essa gravação. Nenhuma branch, commit remoto, release ou execução de Actions nova foi publicada por este trabalho. É necessário liberar acesso de escrita ao repositório, incluindo alterações de workflows. O código-fonte entregue contém o workflow pronto para testar/publicar os cinco arquivos individualmente quando importado.

## Otimizações investigadas e limites

- ABI splits preservam todas as bibliotecas/plugins/codecs de cada arquitetura; Media3, LibVLC, ONVIF e RTSP permanecem nas mesmas versões.
- Minificação e resource shrinking permanecem desativados. Não foi removido nenhum codec ou recurso.
- O Gradle informou que não conseguiu strip das bibliotecas neste ambiente e as empacotou intactas. Inspeção `readelf -SW` de todas as 12 bibliotecas: libvlc.so e libvlcjni.so já não têm `.debug*`/`.symtab`; libc++ de ARM64 e x86_64 ainda contém símbolos de depuração/estáticos.
- Seções de debug + `.symtab` + `.strtab` em libc++: **7.915.227 bytes em ARM64** e **7.677.441 bytes em x86_64**. São uma oportunidade adicional de redução (estimativa por soma das seções, não um APK otimizado medido). O stripping com ferramentas NDK requer medição de resultado e validação de JNI/player antes de adoção. Instalar um NDK completo pode aumentar preparação/download do CI; não foi imposto nesta release.
- Compressão/extração das bibliotecas nativas, AAB e R8 são alternativas futuras, com efeitos sobre instalação, distribuição e compatibilidade; não foram ativadas.
- O tempo local de 2m26s inclui testes e cache/dependências de um ambiente distinto do Actions anterior. Não é evidência de ganho ou regressão de tempo do CI. Workflow remoto novo bloqueado por permissão.
- Sem teste físico de instalação, câmera, player ou decodificação H.264/H.265. Preservar os binários não substitui esses testes. Verificar câmera real e atualização assinada com a chave original antes de considerar a distribuição validada.

Resultados relevantes para o Vigia IA documentados em VIGIA_IA_INTEGRATION.md; nenhum código do Vigia IA foi alterado.

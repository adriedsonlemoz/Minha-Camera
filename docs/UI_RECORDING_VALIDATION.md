# Validação 1.4.8+18

## Resultado reproduzível

- `testProtocol testReleaseUnitTest assembleRelease --no-daemon --build-cache`: BUILD SUCCESSFUL, 39 s, 49 tarefas (23 executadas, 26 atualizadas).
- Sete verificações de protocolo/arquivos aprovadas: a nova verificação usa arquivos sintéticos para testar isolamento de sessões, preservação de bytes/extensão, remoção de arquivos vazios e nomes sem credenciais. Não é teste de decodificação ou gravação nativa.
- `testReleaseUnitTest`: NO-SOURCE; o projeto não contém testes JUnit Android nesta variante.
- XML, versionamento, configuração de build e `git diff --check`: aprovados.
- Cinco APKs release gerados; CRC, alinhamento, assinatura, applicationId, versionName/code, ABIs, bibliotecas nativas e igualdade de código/recursos aprovados pelo verificador.
- Assinatura coincide com o APK **local entregue anteriormente** de 1.4.7+17. Isto não comprova coincidência com o APK produzido em outro runner GitHub. A incompatibilidade histórica com o certificado remoto 1.4.6 continua documentada em BUILD_VALIDATION.md.

| Variante | MB decimais | Redução ante Universal |
|---|---:|---:|
| Universal | 217,59 | 0% |
| arm64-v8a | 62,74 | 71,17% |
| armeabi-v7a | 47,89 | 77,99% |
| x86_64 | 70,96 | 67,39% |
| x86 | 58,30 | 73,21% |

## Escopo e limitações

As sugestões RTSP agora são uma lista suspensa editável. O player exibe carregamento até o primeiro quadro renderizado pelo Media3 ou até uma imagem efetivamente exibida pelo LibVLC. Mantidos fallback, autenticação, descoberta, H.264 e H.265; nenhuma biblioteca nativa foi removida ou alterada.

Gravar usa `MediaPlayer.record` do LibVLC 3.7.7, no mesmo player. Quando o Media3 está ativo, a confirmação troca o mecanismo e reconecta ao mesmo endereço antes de gravar. O indicador REC depende da confirmação nativa; há tratamento de falta de espaço, parada e fechamento antes da publicação do arquivo. O formato é escolhido pelo LibVLC conforme o fluxo, sem transcodificação pelo aplicativo. A reprodução do arquivo depende de um player externo que suporte seu codec/container.

Gravações são locais, agrupadas por dia, com exportação pelo seletor Android, compartilhamento e exclusão confirmada. A gravação termina ao sair da tela, inclusive ao abrir a biblioteca; não há serviço de gravação contínua em background. Desinstalar pode apagar arquivos privados: exporte os arquivos que deseja manter.

Imagem superior/inferior recorta visualmente um fluxo com lentes empilhadas, como o da captura recebida; não separa canais, não controla PTZ e não altera o arquivo gravado. Histórico do cartão SD/NVR não foi implementado: exige conhecer fabricante, modelo e API de reprodução.

Não houve acesso à câmera física do usuário. A validação visual e a gravação RTSP em execução ainda não foram comprovadas; a tentativa de Android virtual é registrada abaixo. Não declarar estes recursos testados em dispositivo com base na compilação ou nos testes de arquivos.

## Impedimentos de execução e publicação

Foi instalado Android Emulator 37.2.12 com imagem API 29 x86_64. O ambiente não oferece `/dev/kvm`; a execução usou emulação por software. Após corrigir dependência do Qt e bloqueio de snapshot, o sistema reportou boot completo (122.673 ms), mas a sessão de instalação/preparação não chegou ao estado pronto para testar o aplicativo e foi interrompida. Não foram obtidos screenshots válidos do novo app, confirmação nativa de REC ou arquivo de vídeo decodificado. O roteiro com duas imagens RTSP sintéticas foi preparado, mas não concluído. Gravação H.264/H.265, recortes, compartilhamento e exportação requerem validação em aparelho antes de afirmar compatibilidade operacional.

O repositório remoto foi conferido em `277ad2368fbf81440ad5d79720bfe106932b1e05` (1.4.7+17 importada pelo GitHub Manager), com conteúdo idêntico à entrega anterior. Nova tentativa de escrita via GitHub retornou `403 Resource not accessible by integration` em `POST /repos/adriedsonlemoz/Minha-Camera/git/trees`. Não houve alteração de branch remota nem execução remota deste build. O código completo é fornecido em ZIP para importação pelo GitHub Manager.

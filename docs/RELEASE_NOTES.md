# Minha Câmera 1.4.6 (versionCode 16)

**Data:** 2026-10-11

## Motivo: build #9 demorando e cancelado

O log mostrou que a compilação funcionou (`BUILD SUCCESSFUL in 2m 8s`, às 05:00:45 UTC), mas o job foi cancelado logo após. O ZIP de logs não informa quem ou o que iniciou o cancelamento, portanto não atribuir a causa ao código.

### Tempos observados no build #9 (aproximados)
- Preparação do Android SDK e instalação da plataforma: **13 s**.
- Gradle: **2 min 8 s** (configuração aproximada ~54 s, tarefas ~75 s).
- Também houve download do Gradle 8.13 (~137 MB), embora parte das dependências já tivesse cache.

## Alterações

1. Habilitada cache de tarefas Gradle via `org.gradle.caching=true` e `--build-cache`.
2. Atualizada a ação `gradle/actions/setup-gradle@v4` para `@v6`, que gerencia Gradle User Home e build cache em jobs completos.
3. Adicionada medição de tempo do processo Gradle no passo de compilação.
4. Mantido LibVLC 3.7.7, sem sacrificar a compatibilidade H.265 para reduzir tempo de maneira não comprovada.
5. Sincronizada a sequência de versões 1.4.6+16.

## Teste esperado

1. Publique o código e execute o workflow sem cancelá-lo após `BUILD SUCCESSFUL`.
2. Espere os passos Nomear APK, Publicar APK e pós-etapas concluírem; baixe o artefato na execução concluída.
3. Execute um segundo build a partir do mesmo código para comparar os tempos (o primeiro pode povoar o cache).
4. Compare `Tempo do Gradle:` no log e procure `FROM-CACHE` nas tarefas.
5. Se continuar lento, exporte o ZIP dos dois logs completos para identificar quais tarefas continuam custosas.

## Limites de validação

Não foi possível executar o Android Gradle Plugin com SDK real neste ambiente. A melhoria de tempo ainda depende de medições no GitHub Actions. O APK continua sendo assinado pela configuração debug do runner; a assinatura estável permanece pendente.

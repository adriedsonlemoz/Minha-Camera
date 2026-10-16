# Build APK 45: correção e validação da versão 1.4.45+55

Data: 2026-10-16.

## Falha original

O workflow **Build APK 45** falhou em `:app:compileReleaseKotlin`, pois os relatórios RTSP introduzidos em `MainActivity.kt` continham novas linhas reais entre aspas regulares Kotlin nas linhas 483–519. As mensagens `Expecting '"'`, `Expecting ')'`, `Unresolved reference` são consequência desses literais inválidos.

## Alteração

Os cinco literais afetados foram substituídos por `\n` ou `\n\n`. O fluxo RTSP, as ações do usuário, a assinatura e a orientação não foram alterados. Foi acrescentado `scripts/check_kotlin_strings.py` e sua suíte de regressão no preflight de `.github/workflows/build-apk.yml`.

## Verificações locais

- `scripts/check_kotlin_strings.py`: verifica 55 arquivos Kotlin e não encontra strings quebradas.
- Os oito defeitos detectados pelo preflight na versão 1.4.44 anterior desapareceram após a correção.
- Parser PSI do compilador Kotlin: 55 arquivos Kotlin, 0 erros sintáticos.
- Quatro testes da nova suíte passam.
- `scripts/check_version.py`, `scripts/check_android_resources.py`, `scripts/check_kotlin_imports.py`, `scripts/check_build_config.py` e testes de regressão existentes: executados nesta versão (detalhes da execução nesta conversa).

## Pendências

Não há `gradlew` no ZIP de origem nem Gradle/Android SDK instalado neste ambiente; por isso a tarefa `assembleRelease`, os testes instrumentados e o teste no aparelho dependem do GitHub Actions.

## Resultados adicionais desta entrega

- `python3 -m unittest discover -s tests -p 'test_*.py' -q`: **76 testes aprovados**.
- `bash scripts/test_protocol.sh`: verificações Kotlin/JVM de protocolo concluídas com sucesso, com avisos de API Java obsoleta não bloqueantes.
- Parser PSI do compilador Kotlin: **55 arquivos**, **0 erros sintáticos**.
- Parse XML: **71 arquivos válidos**.
- Verificação YAML: workflow reconhecido pelo parser PyYAML.
- Testes históricos adaptados às telas por cards e ao versionamento dinâmico, sem recriar o botão antigo "Mais".

# Validação Minha Câmera 1.4.35+45 · 2026-10-15

## Evidência do Build APK 35
- Logs recebidos: `Minha-Camera-Build-APK-35-logs.zip`, 16 arquivos, incluindo log da tarefa de compilação.
- O job concluiu o teste de protocolos e a etapa de assinatura. Falhou em `:app:compileReleaseKotlin`.
- Kotlin: `PhoneHeadlessPreview.kt:11:19` e `:13:19` com `Conflicting import: imported name 'SystemClock' is ambiguous.`
- Causa verificada no ZIP 1.4.34: `import android.os.SystemClock` apareceu duas vezes no mesmo arquivo.

## Correção
- Removido o import redundante, preservando a utilização da API no consumidor EGL da câmera com tela apagada.
- Criado `scripts/check_kotlin_imports.py` e integrado ao preflight GitHub Actions.
- Testes de regressão verificam duplicação literal, import com nomes conflitantes, imports com alias e o projeto completo.
- Versionamento e metadados sincronizados em 1.4.35+45.

## Limites de validação
- Testes estáticos e Kotlin/JVM locais não equivalem a um build Android de release. Reexecutar o GitHub Actions e testar a câmera no Redmi A3.

## Verificações locais executadas
- `python3 scripts/check_kotlin_imports.py`: **aprovado**, 47 arquivos Kotlin sem imports duplicados/ambíguos.
- `python3 scripts/check_version.py`: **aprovado**, 1.4.35+45.
- `python3 scripts/check_android_resources.py`: **aprovado**, 342 strings.
- `python3 scripts/check_protocol_sources.py`: **aprovado**, 15 fontes Kotlin sincronizadas.
- `python3 scripts/check_build_config.py`: **aprovado**.
- `pytest -q tests`: **72 testes + 2 subtestes aprovados**.
- `bash scripts/test_protocol.sh`: compilação Kotlin/JVM sem erros e vários grupos de testes passaram, mas a execução completa **excedeu o tempo limite**. Não considerar essa execução integralmente aprovada.
- Full Gradle Android `assembleRelease` não executado neste ambiente; APK e câmera em hardware real pendentes.

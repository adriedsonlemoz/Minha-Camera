# Validação Minha Câmera 1.4.65+75 — Build APK 64

- Evidência: log `Minha-Camera-Build-APK-64-logs.zip`, etapa `:app:compileReleaseKotlin`: `GalleryActivity.kt:313:17 Unresolved reference 'minHeight'`.
- Motivo: atribuição à propriedade Kotlin `minHeight` em `LinearLayout`. Troca para `setMinimumHeight(dp(84))`.
- Preflight novo: `scripts/check_kotlin_view_minimum_size.py` e `tests/test_gallery_build_fix_1465.py` no GitHub Actions.
- Esta verificação estática não substitui a compilação Android do GitHub Actions nem teste de interface em dispositivo.

## Verificações locais executadas

- `python -m unittest discover -s tests -p 'test_*.py' -q`: **154 testes aprovados**.
- `scripts/check_android_resources.py`: 372 strings verificadas.
- `scripts/check_viewer_bindings.py` e `scripts/check_transmitter_bindings.py`: todas as referências verificadas.
- `scripts/check_build_config.py` e `scripts/check_protocol_sources.py`: configuração e fontes consistentes.
- `scripts/check_kotlin_imports.py`, `scripts/check_kotlin_strings.py` e `scripts/check_kotlin_argb_literals.py`: aprovados.
- `scripts/check_kotlin_view_minimum_size.py`: aprovado.
- `scripts/check_version.py`: 1.4.65+75 sincronizado.
- 82 XML validados via `xml.etree.ElementTree`.
- Não foi possível compilar APK Android completo neste ambiente (sem Gradle/SDK). O GitHub Actions precisa recompilar esta versão.

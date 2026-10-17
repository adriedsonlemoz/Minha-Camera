# Validação da correção 1.4.67+77 (Build APK 66)

- Base: código-fonte 1.4.66+76; logs do Build APK 66.
- Erro confirmado: `GalleryActivity.kt:259:13 Unresolved reference 'fillViewport'` em `:app:compileReleaseKotlin`.
- Correção: `HorizontalScrollView.setFillViewport(true)`, preservando o rodapé rolável e botões da Central Multimidia.
- Adicionados `scripts/check_kotlin_scroll_fill_viewport.py`, regressões `tests/test_gallery_build_fix_1467.py`, e chamada ao preflight no GitHub Actions.
- Executar `python3 -m unittest discover -s tests -q`, preflights e, em CI com Secrets, `gradle testProtocol testReleaseUnitTest assembleRelease --no-daemon --build-cache`.
- Pendências: confirmação do build Android Release e testes físicos de ações da galeria/conexão remota.

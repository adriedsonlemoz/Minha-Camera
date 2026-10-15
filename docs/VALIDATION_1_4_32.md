# Validação 1.4.32+42 (2026-10-15)

- H.264/AVC em MediaCodec, rota HTTP local Annex-B e player LibVLC: implementados, **sem playback real testado**.
- Seletor remoto protegido pelo mesmo token privado, sem criptografia HTTP; usar somente hotspot/rede Wi-Fi confiável.
- Qualidade, FPS, cronômetros e diagnóstico EGL/watchdog implementados.
- `pytest -q`: **63 testes aprovados + 2 subtestes** (estruturais, não Android instrumentado).
- `bash scripts/test_protocol.sh`: **aprovado**, incluindo autenticação MJPEG/H.264/status e rotas de controle HTTP simuladas, além de teste de normalização AVCC/Annex-B em Kotlin/JVM.
- `python3 scripts/check_version.py`: **aprovado**, metadados 1.4.32+42 consistentes.
- `python3 scripts/check_android_resources.py`: **aprovado**, 309 strings verificadas.
- Kotlin PSI: **36 fontes e 0 erros de sintaxe**.
- XML: **65 arquivos válidos**.
- GitHub Actions e APK Android assinado: **ainda não executados nesta versão**. Requer os Secrets já existentes.
- Limite: dispositivos podem não ter encoder AVC acelerado compatível com ByteBuffer NV21/YUV420; fallback MJPEG, sem afirmar desempenho do Redmi A3.

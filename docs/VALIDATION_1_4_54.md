# Validação 1.4.54+64

- Quatro relatórios 1.4.53 analisados. `OPTIONS 200` e `DESCRIBE 200` com SDP H.265; sem vídeo no VLC após tentativas TCP/UDP.
- Corrigida falha encontrada no código anterior: `buildAuth` calculava HA2 com `DESCRIBE` para todas as etapas, incluindo `SETUP`/`PLAY`; esta sondagem produzia resultados inválidos.
- Correção: métodos e URIs correspondentes, nonce-count incremental e cnonce de sessão; DESCRIBE autenticado no mesmo socket do 401 quando viável, fallback em reconexão.
- VLC: opções de credenciais passadas para núcleo LibVLC no modo alternativo, além de opções da mídia, preservando URL e senha salvas.
- Testes Kotlin/JVM simulam desafios 401 no mesmo socket ou socket fechado, e SETUP 200/461 com verificação de Digest em DESCRIBE, SETUP, PLAY.
- **Limitações:** sem câmera acessível neste ambiente; diagnósticos e player devem ser conferidos em aparelho. Sem APK Android compilado localmente.

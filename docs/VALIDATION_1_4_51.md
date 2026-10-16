# Validação 1.4.51+61 — 2026-10-16

- Diagnóstico do usuário: OPTIONS RTSP/1.0 200 OK no endereço da câmera 209; DESCRIBE indica desafio de autenticação; LibVLC sem bytes/quadros.
- Código atualizado: RtspProbe reabre sessão quando o servidor fecha após 401; variação Digest legada e opções de credenciais no LibVLC; relatório sem credenciais.
- Teste de regressão: servidor local que desafia e fecha a primeira conexão, recebe autenticação e responde SDP na segunda.
- A comprovação de funcionamento com a câmera física e a compilação Android/Gradle ainda são pendentes.

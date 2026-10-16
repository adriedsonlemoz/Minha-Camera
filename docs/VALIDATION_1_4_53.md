# Validação 1.4.53+63

- Entrada: relatório de 16/10/2026 da Câmera 209 com `OPTIONS 200`, `DESCRIBE 200` autenticado, SDP H265 no caminho `/`, LibVLC com 0 bytes/quadros.
- Correção: fallback de credenciais em RTSP via opções nativas do LibVLC sem login dentro da URL de mídia, mantendo cadastro e transição entre TCP/UDP.
- Diagnóstico: após DESCRIBE 200, tentativa limitada de SETUP/PLAY RTP/TCP intercalado e detecção breve de dados, usando exclusivamente o mesmo servidor e porta RTSP; não exporta credenciais ou Session-ID.
- Testes de simulação: 401 Digest, 200 DESCRIBE, SETUP/PLAY 200 com dados RTP e SETUP 461 sem RTP. Confira scripts/test_protocol.sh e tests/RtspSessionSmoke.kt.
- Pendente: Gradle assembleRelease, APK assinado no CI e teste físico com a câmera. Não declarar compatibilidade H.265 comprovada.

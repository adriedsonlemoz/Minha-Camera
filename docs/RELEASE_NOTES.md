# Minha Câmera 1.4.1 (versionCode 11)

**Data:** 2026-10-11

Nesta versão, a busca automática passa a combinar descoberta ONVIF WS-Discovery com varredura TCP da sub-rede IPv4 real. Câmeras anunciadas apenas por ONVIF aparecem mesmo quando o RTSP não está nas portas convencionais. Mais portas RTSP são verificadas, sem descartar a segunda porta aberta do mesmo dispositivo. A autenticação RTSP e a leitura de respostas SDP foram corrigidas.

**Validação realizada:** testes simulados locais com `scripts/test_protocol.sh` usando o compilador Kotlin: leitura RTSP com corpo UTF-8, negociação Digest alternativa, XML WS-Discovery `ProbeMatch/XAddrs`, consulta ao XAddr ONVIF anunciado, perfis H.264 e cálculo de sub-redes /23, /24 e /16. Verificação automática de versão com `scripts/check_version.py`.

**Validação pendente:** compilação Android real via GitHub Actions e teste em Wi-Fi com pelo menos uma câmera ONVIF e outra RTSP sem ONVIF. Os testes simulados não comprovam funcionamento no roteador/câmera reais.

**Conhecido:** o erro H.265 `missing sprop-vps` não foi corrigido nesta versão.

**Integração Vigia IA:** os algoritmos de descoberta e as lições de autenticação poderão ser adaptados posteriormente ao projeto Flutter/Android, após testes reais.

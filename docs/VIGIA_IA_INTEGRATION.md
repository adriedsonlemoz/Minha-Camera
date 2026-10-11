# Descobertas do Minha Câmera úteis para o Vigia IA

Atualizado em **1.4.2+12** (2026-10-11).

Este material serve como registro técnico: **não significa que o Vigia IA foi modificado**.

## Descoberta automática e estabilidade (1.4.1)

- `WsDiscovery.kt`: emitir `Probe` ONVIF UDP/3702, interpretar `ProbeMatch` e `XAddrs`, respeitar Wi-Fi e multicast.
- `NetUtil.kt` e `ScanActivity.kt`: consultar rede/máscara IPv4 em vez de presumir `/24`; limitar simultaneidade e tempo de varredura; juntar ONVIF e resultados TCP por IP/porta; cancelar trabalhos anteriores.
- `OnvifClient.kt`: obter perfis e URLs RTSP reais da câmera, evitando caminhos presumidos quando ONVIF responde.
- `RtspProbe.kt`: negociar Basic/Digest RTSP, respeitar `Content-Length` em bytes e desafios de autenticação.

## Reprodução e diagnóstico H.264/H.265 (1.4.2)

- `RtspCodec.kt`: detectar o codec **de vídeo** pelo SDP (`m=video` + `a=rtpmap`), ignorando rtpmap de áudio. Não determinar o codec apenas pelo nome do caminho ou pelo retorno HTTP/RTSP 200.
- `ScanActivity.kt`: quando o primário é H.265, testar o caminho secundário antes de salvá-lo como principal; usar H.264 se confirmado pelo SDP. Não presumir que todo substream é H.264.
- `PlayerActivity.kt`: manter Media3 como padrão, tentar URLs alternativas e, se necessário, abrir com LibVLC para melhorar compatibilidade com HEVC/RTSP. Prever timeout, cancelamento de callbacks obsoletos e liberação de recursos.
- Nunca publicar URLs RTSP com credenciais em logs, relatórios de erro ou analytics. Preferir um armazenamento protegido por keystore/criptografia com processo de migração dos dados legados.

## Atenções na adaptação para Flutter/Android

1. Extrair descoberta e negociação de rede em um módulo Android testável antes de expor por platform channels ao Flutter.
2. Medir tamanho final do pacote, ABIs suportadas, consumo de CPU/GPU, memória, bateria, latência, aquecimento e autonomia ao usar LibVLC. Considerar distribuição ABI-specific em App Bundle.
3. Priorizar H.264 quando qualidade, recursos do dispositivo e uso contínuo favorecerem eficiência; HEVC deve ter testes reais em cada plataforma/hardware de interesse.
4. Câmeras RTSP não anunciam necessariamente ONVIF; manter cadastro manual e os dois mecanismos de descoberta.
5. Para streams RTSP com SDP problemático, comparar VLC vs Media3 e registrar o SDP **com IP/credenciais mascarados** antes de decidir por workaround.
6. Uma camada Flutter baseada em libVLC precisa de uma integração nativa adequada para gerenciar Surface, ciclo de vida e áudio. Não copiar diretamente código `PlayerActivity` para o app Flutter.

## Estado de validação

Os testes simulados da 1.4.2 validam o **parser SDP e a sondagem RTSP**, não a decodificação real H.265 ou a integração Android LibVLC. Testes em câmeras reais são obrigatórios antes de portar ao Vigia IA.

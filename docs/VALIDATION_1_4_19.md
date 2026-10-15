# Validação 1.4.19+29 — 2026-10-15

## Escopo
- Indicadores discretos de FPS transmitidos e FPS efetivamente exibidos, com amostragem a cada 2 s.
- Estimativa do atraso JPEG pronto → recebimento, com timestamps monotônicos do emissor; não confundir com latência total da câmera.
- Reconexão automática com espera 1/2/4/8 s e cancelamento em onPause/Retry; ao retomar, consultar e vincular Wi-Fi atual; 403/404 não provocam repetição automática.
- Mantidas cinco ações, gravação MP4 e dupla experimental, baterias no vídeo, ONVIF/RTSP, assinatura estável e QR.
- Sem dependência externa ou permissão nova.

## Testes locais
- 26 Kotlin de produção passaram pela checagem PSI sintática, sem erros.
- 50 XML de recursos validados com parser XML.
- `scripts/check_version.py` e `scripts/check_build_config.py`: aprovados.
- Cálculos JVM puros (FPS, backoff, offset monotônico, janela de atraso): aprovados.
- A suíte simulada de protocolos imprimiu todos os casos aprovados, incluindo MJPEG, ONVIF, RTSP e autenticação; a invocação no ambiente retornou limite de execução externo, portanto não foi possível verificar o exit code do processo completo.
- ZIP validado por CRC e inspeção de arquivos.

## Pendente
- Build Gradle completo Android e aapt: não disponíveis no ambiente. Executar `gradle testProtocol testReleaseUnitTest assembleRelease --build-cache` no GitHub Actions.
- APK Release real e prova de instalação/assinatura no aparelho.
- Verificar atraso aproximado de entrega e reconexão com dois celulares, especialmente em Wi-Fi com baixa qualidade ou ao alterar o IP.

## Segurança e limites
- O stream MJPEG HTTP permanece sem criptografia, protegido por token e limitado à rede local. A medição temporal não adiciona rede externa nem exige GPS.
- Só há reconexão automática quando a tela Assistir está aberta; sem serviços em segundo plano.
- O indicador de atraso é aproximado e pode mostrar `--`. Mesmo quando aparece um número, não mede a latência total câmera → tela.
- Certificado SHA-256 e Secrets de assinatura não foram alterados; nenhuma chave, Secret, keystore nem valores privados integram o ZIP.

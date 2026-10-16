# Métodos de conexão, compatibilidade e limites (1.4.52)

## Evidência da câmera fornecida
- Embalagem JORTAN **JT-9686**; etiqueta adicional **AJ-A13-1H**.
- Adesivo **Yoosee**, codec **H.265**, alimentação **12 V DC**, consumo **7 W máx.**, proteção **IP66**, conforme impresso.
- Firmware e portas exatas **não confirmados** pelo modelo impresso.
- Diagnóstico real 1.4.50: `OPTIONS RTSP/1.0 200 OK` em 192.168.100.209:554, `DESCRIBE` com autenticação não confirmada; zero bytes VLC.

## Compatibilidade implementada
- **RTSP**: LibVLC TCP e UDP por URL com autenticação RTSP. `RtspProbe` analisa DESCRIBE/SDP; agora só confirma vídeo se houver `m=video`.
- **HTTP e HTTPS**: URL direta de vídeo/MJPEG. **HLS** `.m3u8` e **MPEG-DASH** `.mpd` usam o demux do LibVLC; não há servidor/transcodificador incorporado.
- **RTMP/RTMPS**, **RTP/UDP**: aceitos pelo cadastro e enviados ao LibVLC, suporte final DEPENDE dos módulos nativos e do formato/firmware; experimentais.
- **ONVIF/WS-Discovery**: enumeração de serviços e perfis para obter URL real; não é player.
- **WebRTC, P2P proprietário (Yoosee/XYZ)**: não implementados. Exigem sinalização autenticada/SDK ou protocolos documentados e não podem ser generalizados por simples URLs.

## Verificações e segurança
- Scan local das portas 554, 8554, 10554, 8555, 7447, 80, 8080 e 5000; porta TCP aberta não prova serviço.
- Login NVR/RTSP pode ser distinto do app Yoosee. Não adivinhar senhas, usuários nem executar login padrão automaticamente.
- Cadastro preserva a URL/alternativas; não altera antes de confirmação de vídeo. Entrada HTML/URL é validada por esquema conhecido/host, sem executar ações arbitrárias.
- Exportação de diagnóstico deve ocultar credenciais e tokens. Sem teste físico da câmera e sem compilação Android local.

## Fontes técnicas consultadas em 2026-10-16
- Android Media3 RTSP: https://developer.android.com/media/media3/exoplayer/rtsp
- VideoLAN live555: https://github.com/videolan/vlc/blob/master/modules/access/live555.cpp
- Guia Yoosee NVR: https://yoosee.app/how-to-connect-the-camera-with-nvr/
- Yoosee ONVIF/RTSP pesquisa comunitária: https://github.com/victorbillyph/Yoosee-camera-documentation
- Especificação Android HLS: https://developer.android.com/media/media3/exoplayer/hls

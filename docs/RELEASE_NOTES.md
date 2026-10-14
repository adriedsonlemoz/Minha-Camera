# Minha Câmera 1.4.10 (versionCode 20)

Data: 2026-10-14.

**Novo:** um Android pode transmitir a própria câmera a outro Android pela rede local. Emissor: «Usar este celular como câmera», permitir câmera, copiar URL e manter a tela aberta. Receptor: «Adicionar outro celular» e colar a URL. Preview MJPEG com câmera traseira/frontal; sem IA, áudio ou gravação neste modo.

**Segurança:** token aleatório de 128 bits persistente no aparelho e revogável, 2 clientes no máximo, somente IPv4 Wi-Fi/AP, endpoint HTTP sem TLS. A mesma URL funciona em novas sessões no mesmo IP até o token ser renovado; em caso de troca de IP, atualizar o cartão no monitor. Usar apenas rede confiável.

**Preservado:** RTSP, ONVIF, H.264/H.265, Media3/LibVLC e gravações existentes; não foram introduzidas alterações nas chaves de assinatura. Build Android e testes físicos pendentes.

Detalhes, roteiro e validações: [PHONE_CAMERA_IMPLEMENTATION.md](PHONE_CAMERA_IMPLEMENTATION.md).

---

# Minha Câmera 1.4.9 (versionCode 19)

Data: 2026-10-11.

Correção de rotação: controles reorganizados em uma única linha na horizontal sem recriar a Activity, desconectar o player ou interromper a gravação. A barra superior fica mais compacta; ícones acompanham o texto.

Lentes superior e inferior: enquadramento pela proporção real do vídeo, sem zoom. Toda a metade escolhida permanece visível, centralizada com barras quando necessário. Imagem inteira também usa FIT. Não é possível mostrar um vídeo vertical preenchendo toda uma tela horizontal sem cortar ou deformar: barras preservam a imagem.

Menu Identificar câmera e serviços: consulta GetDeviceInformation, GetServices e GetCapabilities. Exibe identidade informada pelo firmware e serviços anunciados de PTZ, gravação, busca e replay. Consulta somente leitura, sem mover a câmera. Não determina qual lente se move, não substitui teste PTZ e não implementa o histórico proprietário do Yoosee. Ausência na resposta não comprova falta do recurso.

ONVIF, RTSP, H.264/H.265, Media3, LibVLC, gravação local, cinco variantes APK, minSdk e configuração de assinatura preservados. Resultados e limitações em PLAYER_FIT_VALIDATION.md.

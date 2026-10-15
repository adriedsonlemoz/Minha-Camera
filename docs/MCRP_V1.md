# Minha Câmera Remote Protocol (MCRP) v1

Implementado no Minha Câmera 1.4.36 e consumido por Vigia IA 1.0.244.
**Compatibilidade:** as rotas e o QR legados `http://IPv4:8765/live/{token}` permanecem ativos; `vigiaia://pair` continua exclusivo do pareamento antigo do Vigia IA. MCRP é opt-in.

## Transporte e autorização
- HTTP em rede local IPv4/hotspot (sem internet). O servidor usa porta 8765 e token aleatório de 32 dígitos hexadecimais, compartilhado pelo QR. Em MCRP v1 o token está na URL; tratar o QR e os links como credenciais, não transmiti-los pela internet nem registrá-los em logs. Sem TLS: adequado somente para rede Wi-Fi confiável, não para rede pública. O cliente do Vigia IA restringe hosts a IPv4 privado.
- `GET /control/{token}/mcrp/info`: JSON `{ "protocol":"mcrp", "version":1, "device":{"model":"..."}, "stream":{"codec":"mjpeg","mjpeg":"/live/{token}","h264":"/h264/{token}"}, "capabilities":{...} }`. Os atributos de `capabilities` informam a existência da ação, não que o comando já foi concluído.
- `GET /control/{token}/status`: mantém cabeçalhos X-Stream-Codec, X-Camera-Facing, X-Quality-Profile, X-FPS-Target, X-Recording, X-Recording-Seconds, X-Transmission-Seconds, X-Battery-Level etc. O receptor consulta novamente após POST. Não presumir sucesso da câmera apenas pelo HTTP 204.
- `GET /control/{token}/diagnostic`: texto sem credenciais.
- `GET /live/{token}`: MJPEG multipart. `GET /h264/{token}`: H.264 Annex-B experimental, ativo somente quando selecionado. A escolha do codec ainda é global ao transmissor em v1.

## Ações legadas mantidas
Somente POST para `/control/{token}/record/start|stop`, `/eco/enable|disable`, `/camera/back|front`, `/photo`, `/quality/{360|480|720|768|1080}`, `/fps/{5|10|15|24|30}`, `/codec/mjpeg|h264`. HTTP 204 = comando aceito; 409 = indisponível/bloqueado (p.ex. gravação ativa). A captura pode levar tempo para assumir um novo estado.

## Biblioteca remota
- `GET /control/{token}/mcrp/media`: JSON `{ "protocol":"mcrp", "version":1, "items":[{"id":"...","name":"...","mime":"video/mp4","size":123,"modified":timestampMs}] }`, até 100 arquivos recentes.
- `GET /control/{token}/mcrp/media/{id}`: bytes de arquivo com `Content-Length`, `Accept-Ranges: bytes`, `Range: bytes=início-fim` opcional e HTTP 206/416; só IDs retornados pela biblioteca, sem aceitar caminhos enviados pelo usuário. Acesso remoto somente a gravações e fotos criadas pelo próprio aplicativo e visíveis ao app. Arquivos pendentes/incompletos não são listados. Sem rota para excluir ou modificar arquivos.
- A biblioteca só atende enquanto o serviço de transmissão está ativo e autorizado. A transferência usa a própria rede local, podendo competir com o vídeo: evitar grandes downloads durante a cicloviagem.

## Evolução segura
- Protocolos futuros devem anunciar `version`, negociar recursos no `info`, manter as rotas v1 enquanto clientes anteriores estiverem em uso, e adicionar autenticação criptográfica de pareamento se houver uso em redes não confiáveis.
- O cliente do Vigia IA oferece visualização MJPEG e H.264 e ações de controle, mas não injeta essa nova fonte no pipeline de detecção de IA nesta versão. A IA continua opcional e independente.
- Validar no Redmi A3 e no celular receptor antes de afirmar FPS, calor, latência, playback MP4 ou suporte H.264 por hardware.

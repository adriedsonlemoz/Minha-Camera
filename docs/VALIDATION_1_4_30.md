# Validação Minha Câmera 1.4.30+40 — 2026-10-15

Mudanças: início manual da transmissão; SurfaceTexture com EGL consumidor; foto nativa remota; resolução/FPS/troca de câmera remotos; REC timer; fullscreen e novidades.

Status de compilação Android: pendente, sem Android SDK/Gradle assinado no ambiente local. Testes estáticos e de protocolo detalhados no resultado da entrega.

## Verificações locais realizadas
- `scripts/check_version.py`: aprovado, versão e código 1.4.30+40 sincronizados.
- `scripts/check_android_resources.py`: aprovado, 293 strings.
- `scripts/check_build_config.py`: aprovado, compileSdk 36/targetSdk 34.
- `pytest tests`: 57 testes aprovados mais 2 subtestes (verificação estática de UI, recursos, segurança e fluxo).
- `scripts/test_protocol.sh`: compilação Kotlin/JVM com stubs e testes de rede simulados aprovados, incluindo POST de foto, FPS, qualidade e troca de câmera com token válido/inválido.
- Análise Kotlin PSI: 33 fontes sem erros sintáticos.
- Validação estrutural XML: 63 arquivos válidos.
- Não houve build Gradle Android assinado nem teste físico; os testes simulados não comprovam estabilidade da câmera do Redmi A3.

## Limitações e regras de segurança
- A foto remota é salva no **transmissor**: no Android 10+, em `Pictures/MinhaCamera`; no Android 7–9, em pasta privada do aplicativo.
- A resposta HTTP 204 reconhece o pedido de foto, não confirma sua gravação; falhas posteriores são anotadas localmente no serviço.
- A foto interrompe momentaneamente a prévia e não pode ser solicitada durante REC. Alterações de lente e resolução também são bloqueadas durante REC.
- `360p/480p/720p` são perfis desejados. O serviço seleciona o tamanho de prévia permitido mais próximo, até 1280×720, sem inventar resolução.
- O consumo EGL corrige um possível gargalo concreto no código, mas não constitui prova de que todo travamento decorra exclusivamente dele. Testar ao menos 30 min com tela apagada, Eco ligado/desligado, monitorando bateria e temperatura.
- Reinicialização automática local limitada a 3 tentativas somente se o serviço estiver sem gravação e sem fotos/trocas em curso.

# Validação Minha Câmera 1.4.33+43 · 2026-10-15

## Implementado
- Tela inicial unificada para celulares e câmeras RTSP, sem abas separadas.
- Ações de cada câmera Abrir / Copiar / Editar / Excluir, com confirmação ao excluir.
- Central Multimídia: gravações internas RTSP e seleção de fotos/vídeos do Android por SAF.
- Configurações: sistema, claro, escuro e preto AMOLED experimental; Sobre das últimas três versões; chave Pix e valores indicativos de apoio via Google, **sem sistema de compra ativado**.
- Servidor de câmera em primeiro plano recebe REC, Eco, câmera frontal/traseira e foto, além dos controles já existentes de qualidade/FPS.
- Resposta de erro X-Control-Reason quando REC bloqueia foto, troca de câmera ou resolução; receiver mostra razão do bloqueio.
- Celulares conectados enviam nome do modelo e resolução de preview (headers sanitizados) para identificação na lista; RTSP não inventa metadados.
- Botão Refazer renomeado Reconectar; apenas restabelece o receptor, não reinicia o transmissor.
- Preservados MJPEG, H.264 experimental, PiP, diagnósticos, serviço de tela apagada e assinatura estável.

## Limitações
- Mudanças remotas sensíveis continuam bloqueadas durante REC ativo para preservar a gravação MP4.
- Metadados remotos dependem de servidor acessível e tempo de resposta; podem não aparecer quando o transmissor está offline ou usa APK antigo.
- Opção Google Play com valores sugeridos é informativa; cobrança depende do Play Console e do Google Play Billing. Não há transações simuladas.
- Temas AMOLED: ajuste escuro de janela e navegação, com superfícies de alto contraste. Aparência exata requer teste visual em aparelho.
- Necessário compilar no GitHub Actions e executar teste físico; validações locais apenas estruturais e de protocolo.

## Resultados locais
- `scripts/check_version.py`: **aprovado**, 1.4.33+43.
- `scripts/check_android_resources.py`: **aprovado**, 342 strings, incluindo textos do menu e nova tela inicial.
- `scripts/check_build_config.py`: **aprovado**.
- `python3 -m pytest -q`: **66 testes e 2 subtestes aprovados**.
- `bash scripts/test_protocol.sh`: **aprovado** (simulação Kotlin/JVM, sem Android real).
- Kotlin PSI: **39 arquivos analisados, zero erros de sintaxe**.
- Não foi possível executar `gradle testProtocol testReleaseUnitTest assembleRelease --build-cache`: Gradle/Android SDK indisponível neste ambiente.
- O APK da versão 1.4.33 ainda não foi compilado pelo GitHub Actions; teste físico, compra Google e aparência AMOLED pendentes.

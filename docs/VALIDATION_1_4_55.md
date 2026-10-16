# Validação 1.4.55+65 — 2026-10-16

## Código alterado
- Receptor MJPEG: matriz local de rotação em 90°/180°/270°, espelhamento e enquadramento FIT/FILL; vídeo mantido sem nova codificação.
- Modo imersivo automático em horizontal e, com orientação automática, também em vertical; controles essenciais sobrepostos com saída para os ajustes completos.
- Grade de controles com 66 dp de altura, janelas de seleção ilustradas, diagnóstico remoto exportável com SAF.
- LibVLC H.264: aplicada rotação de View com escala conservadora, mas a superfície de vídeo nativa e sua orientação real não foram testadas.

## Verificações disponíveis
- 112 testes Python do projeto, incluindo 4 novos testes de regressão do receptor: OK.
- Verificações de recursos Android, imports e literais Kotlin, metadados e configuração do build: OK.
- 72 XML de recursos parseados sem erros (validar o total na etapa final de empacotamento).
- Suíte Kotlin/JVM de protocolo: parte dos grupos passou (protocolos, NV21, orientação, MJPEG e reconexão; testes RTSP policy e serviços), porém o comando completo ultrapassou o limite do ambiente durante os grupos finais; portanto NÃO foi aprovado integralmente.
- Android Gradle APK: não executado (comando `gradle` não disponível); não há APK nesta entrega.
- Nenhum teste em Redmi A3, aparelho receptor, rede real ou câmera RTSP foi executado.
- O teste do `scripts/verify_apks.py` depende dos APKs assinados via GitHub Actions e não foi executado.

## Testes recomendados no aparelho
1. Redmi A3 transmitindo MJPEG, receptor em retrato e depois paisagem, verificar entrada automática em modo imersivo.
2. Sair da imersão e garantir que não volte sozinho sem outra rotação; testar os controles Gravar, Foto, Giro e Enquadramento.
3. Giro manual 0/90/180/270 sem recorte inadvertido, com câmera traseira/frontal e espelho habilitado/desabilitado.
4. Tela transmissora apagada, gravação opcional, reconexão e consumo com 720p/10–15 FPS no retrovisor.
5. Exportar diagnóstico no SAF e validar o arquivo TXT; testar H.264 separadamente como experimental.

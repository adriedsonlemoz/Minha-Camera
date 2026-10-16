# Análise do vídeo 1000007854.mp4 (33,928 s)

Fonte: gravação de tela enviada pelo usuário em 16/10/2026.

- ~0–8 s: transmissor MJPEG com prévia local atualizando normalmente. Indicadores de envio variam.
- ~9–12 s: após habilitar "Tela apagada", interface indica estado de inicialização, o controle retorna a "Iniciar" e a prévia deixa de representar um fluxo ativo. Isso confirma falha/interrupção da transição do modo visível para o serviço, mas o vídeo **não identifica a exceção**.
- ~18–34 s: aparelho gira para paisagem e o vídeo da gravação permanece em uma tela de captura orientada verticalmente, produzindo margens pretas. O recorte registrado não permite concluir que o player do Minha Câmera tenha deformado a imagem.
- A gravação é do transmissor. Ela não demonstra a execução de um comando de zoom remoto no receptor: o defeito foi analisado na lógica de status/capacidades e UI da v1.4.58.

Mudanças v1.4.59: EGL pré-inicializado fora do principal, tentativas curtas de reabrir a câmera/porta, SO_REUSEADDR, miniatura local reduzida, status MJPEG periódico e atalhos de zoom +/- e 1×, com zoom local distinto do zoom real. Esses ajustes são preventivos e têm hipóteses técnicas plausíveis, mas a correção total exige novo teste no Redmi A3 e captura de diagnóstico exportado se a falha continuar. Sem medição de bateria/autonomia ou alegação de teste de aparelho.

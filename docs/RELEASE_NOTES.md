# Minha Câmera 1.4.8 (versionCode 18)

Data: 2026-10-11.

Novo visual de cadastro e player; sugestões RTSP em lista editável; carregamento até a primeira imagem; tela cheia e recortes superior/inferior para fluxos com duas lentes empilhadas. Ajustes são de visualização, sem alterar o fluxo gravado.

Gravação do fluxo ao vivo usando MediaPlayer.record(directory) do LibVLC 3.7.7; em Media3, o mesmo URL é reconectado pelo LibVLC. O app só mostra REC após RecordChanged confirmar gravação, solicita record(null) para parar e aguarda fechamento antes de publicar o arquivo. Arquivos privados ganham nomes seguros antes de compartilhar. Espaço livre é monitorado. A sessão termina ao sair do player ou interromper a transmissão.

Biblioteca local organizada por dia, com abrir em player externo, compartilhar, salvar cópia pelo seletor Android e excluir. Não importa gravações antigas da câmera e não promete gravação de fundo/24h. Nenhuma permissão ampla de arquivos, microfone ou armazenamento foi adicionada.

ONVIF, RTSP, H.264/H.265, Media3, LibVLC e APKs por ABI preservados. minSdk 24 e assinatura debug configurada mantidos; continuidade de assinatura com a 1.4.7 local é verificada, e assinatura do Actions antigo continua dependendo da chave original.

Resultados reais e limites: UI_RECORDING_VALIDATION.md. O registro BUILD_VALIDATION.md é histórico da 1.4.7.

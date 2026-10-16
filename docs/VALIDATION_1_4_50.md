# Validação Minha Câmera 1.4.50+60

Data: 2026-10-16

## Mudanças
- Teste RTSP independente no VLC externo, com confirmação quando URL inclui login.
- Alternância manual entre rota padrão do Android e Wi-Fi vinculado; sem alterar URLs ou credenciais.
- Estatísticas nativas do LibVLC (bytes lidos, demux, decodificados, imagens exibidas/perdidas), eventos e versão do app.
- Exibição do código da resposta OPTIONS, sem confundir com a confirmação DESCRIBE. A ausência de resposta DESCRIBE não é rotulada como codec inválido.
- Boas-vindas atualizadas somente para 1.4.50.

## Validação executada
- `python3 -m unittest discover -s tests -p 'test_*.py' -q`: 91 testes aprovados, incluindo regressões dos novos controles.
- `bash scripts/test_protocol.sh`: aprovada simulação Kotlin/JVM de protocolo e detector RTSP (inclusive OPTIONS 401).
- PSI Kotlin: 50 arquivos principais parseados, zero erros sintáticos.
- `scripts/check_version.py`, `check_build_config.py`, `check_android_resources.py`, `check_kotlin_imports.py` e `check_kotlin_strings.py`: aprovados.
- XMLs do Android e integridade CRC do ZIP: aprovados.

## Limitações
- Compilação Android completa com Gradle, execução do VLC externo, compatibilidade com a câmera 209 e rede real não foram verificadas nesta execução.
- Sem captura de logs nativos detalhados do VLC; a API pública expõe estatísticas e eventos de player, não a razão textual completa de todos os erros nativos.
- Media3 1.3.1 RTSP permanece desativado após histórico de falha fatal do parser CSeq.

# Validação 1.4.66+76

- Base: 1.4.65+75.
- Verificar `python -m unittest discover -s tests` e preflights em `scripts/`.
- Renomeação: não substituir arquivos existentes nem remover a extensão, pedir autorização de escrita pelo Android quando exigida.
- Remoto: não publicar credencial de pareamento no cabeçalho; mostrar conectado apenas após HTTP 200 do endpoint MCRP autenticado.
- Pendente: Gradle assembleRelease e testes com dois Android reais (incluindo autorização MediaStore e provedor SAF).
- Aprendizado Vigia IA: separar dispositivos salvos do estado real de sessão e exigir autorização antes de modificar mídia do dispositivo.

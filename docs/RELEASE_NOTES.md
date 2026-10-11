# Minha Câmera 1.4.3 (versionCode 13)

**Data:** 2026-10-11

## Alterações

- **Repaginada a lista principal:** as câmeras salvas agora aparecem em cartões com resumo mais útil e ações rápidas para **Abrir**, **Copiar RTSP** e acessar **Mais** opções.
- **Copiar endereço com senha:** cada câmera salva pode copiar o endereço RTSP completo, com usuário e senha, para poupar tempo em testes, compartilhamento controlado e integração com outros aplicativos.
- **Formulários mais práticos:** a configuração de câmeras encontradas, a adição manual e a edição de câmeras salvas ganharam campos mais organizados, com nome, IP/URL, porta, usuário, senha e caminho RTSP.
- **Senha mais acessível:** os formulários permitem **ver/ocultar**, **copiar** e **colar** a senha diretamente.
- **Sugestões de caminho RTSP:** o campo de caminho oferece opções rápidas (por exemplo `/stream1`, `/onvif1`, `/Streaming/Channels/101`) e a opção **Default**, que preserva a descoberta automática quando nenhum caminho específico for escolhido.

## Validação

- Revisão estática dos arquivos Kotlin/XML e sincronização dos metadados de versão.
- Não foi compilado um APK Android neste ambiente, pois o Android SDK/Gradle e as dependências Maven não estão disponíveis aqui.

## Roteiro de teste no Android

1. Compilar via GitHub Actions e instalar a atualização assinada com a mesma chave da versão anterior.
2. Buscar uma câmera na rede e verificar o novo diálogo: ver/ocultar, copiar e colar senha; escolher caminho sugerido e salvar.
3. Adicionar uma câmera manualmente e testar a opção **Default** versus um caminho sugerido.
4. Na tela **Minhas câmeras**, usar **Copiar RTSP** e colar o endereço em outro local para confirmar que usuário e senha foram copiados.
5. Editar uma câmera salva para alterar senha, porta e caminho, confirmando persistência e reprodução.
6. Confirmar que o player continua reproduzindo as câmeras já salvas, inclusive as que dependem do fallback Media3/LibVLC.

**Integração Vigia IA:** a ergonomia melhorada de cadastro e cópia de credenciais/endereços é útil para o fluxo de configuração do futuro módulo de câmeras do Vigia IA.

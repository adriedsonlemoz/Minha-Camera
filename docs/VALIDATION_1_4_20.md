# Validação 1.4.20+30 — 2026-10-15

## Implementação
- Novo `PhoneEcoPolicy.kt`: ritmo padrão até ~11 FPS (90 ms), Eco até ~5 FPS (200 ms), temperatura elevada até ~3 FPS (300 ms). Valores são limites máximos de publicação, **não FPS garantido**.
- JPEG MJPEG com qualidade 62 (normal), 50 (Eco), 45 (bateria muito quente). O valor é um parâmetro do codificador, não uma medida exata de qualidade visual.
- Alerta discreto na tela do transmissor usando a leitura já existente da temperatura **da bateria** (não da CPU): aviso a partir de 42°C, limitação a partir de 45°C, com histerese (retorno de HOT abaixo de 42°C; desativação do aviso abaixo de 40°C).
- A consulta ao Android continua cacheada por ~15 segundos. Texto calculado apenas quando uma nova leitura entra; MJPEG mantém overlay de bateria e temperatura original quando ativado.
- `Mais → Eco` persiste em `SharedPreferences`, sem reiniciar QR, servidor, player nem MP4. Funciona durante gravação, mas reduzir FPS pode reduzir a suavidade dos vídeos.
- `PhoneDualCamera` agora verifica o intervalo antes de copiar YUV e converter quadros, minimizando CPU no modo duplo; prévia dual atualiza mais lentamente em Eco/alta temperatura.
- Contador de FPS volta a funcionar durante gravação, evitando remoção acidental do ticker pela UI.

## Testes locais executados
- `scripts/check_version.py`: APROVADO para 1.4.20+30.
- `scripts/check_build_config.py`: APROVADO.
- `tests/test_qr_integration.py`: APROVADO.
- `tests/test_preview_integration.py`: APROVADO.
- `tests/test_ui_1414.py`: APROVADO.
- `tests/test_battery_overlay_1418.py`: APROVADO (10 cenários JVM e 10 invariantes estáticas relatadas pela suíte).
- `tests/test_signing_preflight.py`: 16 testes APROVADOS.
- Parser PSI Kotlin: 27 arquivos de produção, sem erros sintáticos.
- XML: 51 arquivos sob `app/src/main`, válidos.
- Integração estática da política Eco (limites, persistência, diálogo e duas câmeras): APROVADA.
- `tests/PhoneEcoPolicySmoke.kt` incluído no target Gradle `testProtocol` e no script local; compilação JVM isolada por `kotlinc` no ambiente local atingiu timeout, portanto esses testes **ainda não têm resultado de execução**.
- ZIP: integridade/CRC e exclusão de keystore, senhas e Secrets verificadas.

## Pendências
- Compilação Android real e execução de `gradle testProtocol testReleaseUnitTest assembleRelease --build-cache` via GitHub Actions.
- Testes de atualização por cima de APK 1.4.19, preservando o mesmo certificado SHA-256.
- Medir consumo CPU, RAM, temperatura, latência e FPS reais em celulares de entrada, rede Wi-Fi local, câmera dupla e gravação MP4.
- Não provocar aquecimento intencional de bateria para simular alertas. Para testar limiares, usar testes controlados com leitura simulada.

## Riscos e limitações
- A redução do FPS não impede a captura contínua necessária à prévia da API Camera antiga; a economia é principalmente no encode JPEG, transporte e processamento no receptor. Não há garantia de queda de consumo sem medição real.
- O controle térmico baseia-se na temperatura da bateria. `null`/valor inválido não são tratados como dados medidos. O Android pode impor limites térmicos adicionais sem avisar.
- MJPEG HTTP e controles de acesso permanecem locais. Nenhuma mudança de permissões, dependências, assinatura ou chave privada.

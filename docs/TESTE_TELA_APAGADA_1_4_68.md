# Teste dirigido: gravação com tela apagada (1.4.68+78)

## Pré-requisitos
- APK 1.4.68+78 compilado e instalado no Redmi A3, receptor na mesma rede Wi-Fi, bateria suficientemente carregada, proteção contra aquecimento (sem sol direto).
- Se falhar: exporte **Diagnóstico** no transmissor via TXT, antes de reiniciar o app, e anexe ao relato; jamais envie credenciais de acesso à câmera.

## Sequência A: passagem MJPEG para serviço
1. Abra **Usar este celular como câmera** na vertical e inicie MJPEG.
2. Ative **Tela apagada** enquanto a Activity estiver visível. Observe: "preparando superfície EGL", "abrindo câmera", "aguardando primeiro quadro" e finalmente quadro publicado no receptor.
3. Apague a tela usando o botão ou aguarde o bloqueio. Deixe 5 minutos e verifique se receptor mantém FPS e recebe quadro novo.
4. Volte ao transmissor, pressione **Gravar** com modo tela apagada ativo, mantenha 1 minuto e encerre. Verifique a presença do arquivo MP4 na biblioteca e sua duração real.
5. Desative Tela apagada. Verifique se a prévia normal reaparece **sem reiniciar o app**.

## Sequência B: repetição e falha
- Repita a transição 5 vezes, deixando 15 segundos entre desligar/ligar. Teste com Wi-Fi local.
- Se não aparecer o primeiro quadro em até 45 s, a UI cancela a migração e aguarda a liberação do serviço antes de abrir a prévia local.
- Registre se o congelamento ocorre ao ativar o serviço, ao apagar fisicamente a tela ou ao iniciar MP4: são caminhos diferentes.
- Exportação registra as fases Camera.open, Camera.startPreview, primeiro quadro e eventual watchdog.

## Com ADB opcional
```bash
adb logcat -c
adb logcat -v threadtime > minha-camera-tela-apagada-logcat.txt
```
Encerre a coleta depois de reproduzir. **Revise e remova identificadores privados** antes de compartilhar o log.

## Testes automáticos reproduzíveis
```bash
python3 -m unittest discover -s tests -p test_background_handoff_1468.py -q
kotlinc app/src/main/java/com/minhacamera/PhoneBackgroundHandoffPolicy.kt tests/PhoneBackgroundHandoffSmoke.kt -include-runtime -d /tmp/phone-handoff.jar
java -jar /tmp/phone-handoff.jar
```
Esses testes validam a política de concorrência, mas não exercitam Camera API/EGL do Android real.

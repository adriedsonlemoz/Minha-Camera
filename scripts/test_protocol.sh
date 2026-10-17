#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT
kotlinc \
  "$ROOT/tests/protocol-stubs/AndroidStubs.kt" \
  "$ROOT/tests/protocol-stubs/SystemClockStub.kt" \
  "$ROOT/tests/protocol-stubs/ContextStub.kt" \
  "$ROOT/tests/protocol-stubs/WifiStub.kt" \
  "$ROOT/tests/protocol-stubs/NetworkStub.kt" \
  "$ROOT/app/src/main/java/com/minhacamera/NetUtil.kt" \
  "$ROOT/app/src/main/java/com/minhacamera/OnvifClient.kt" \
  "$ROOT/app/src/main/java/com/minhacamera/RtspProbe.kt" \
  "$ROOT/app/src/main/java/com/minhacamera/RtspCodec.kt" \
  "$ROOT/app/src/main/java/com/minhacamera/WsDiscovery.kt" \
  "$ROOT/app/src/main/java/com/minhacamera/RecordingFiles.kt" \
  "$ROOT/app/src/main/java/com/minhacamera/VideoGeometry.kt" \
  "$ROOT/app/src/main/java/com/minhacamera/OnvifIdentity.kt" \
  "$ROOT/app/src/main/java/com/minhacamera/PhoneCameraUrls.kt" \
  "$ROOT/app/src/main/java/com/minhacamera/Nv21Frames.kt" \
  "$ROOT/app/src/main/java/com/minhacamera/PhoneBatteryReading.kt" \
  "$ROOT/app/src/main/java/com/minhacamera/PhoneCameraServer.kt" \
  "$ROOT/app/src/main/java/com/minhacamera/PhoneStreamStats.kt" \
  "$ROOT/app/src/main/java/com/minhacamera/PhoneEcoPolicy.kt" \
  "$ROOT/app/src/main/java/com/minhacamera/PhoneAvcAnnexB.kt" \
  "$ROOT/app/src/main/java/com/minhacamera/PhoneOrientationMode.kt" \
  "$ROOT/tests/PhoneOrientationSmoke.kt" \
  "$ROOT/tests/PhoneAvcAnnexBSmoke.kt" \
  "$ROOT/tests/ProtocolSmoke.kt" \
  "$ROOT/tests/PhoneEcoPolicySmoke.kt" \
  -include-runtime -d "$BUILD/tests.jar"
java -jar "$BUILD/tests.jar"
kotlinc "$ROOT/app/src/main/java/com/minhacamera/RtspPlaybackPolicy.kt" \
  "$ROOT/tests/RtspPlaybackSmoke.kt" -include-runtime -d "$BUILD/rtsp-retry.jar"
java -jar "$BUILD/rtsp-retry.jar"
kotlinc "$ROOT/app/src/main/java/com/minhacamera/RtspServiceDetector.kt" \
  "$ROOT/tests/RtspServiceSmoke.kt" -include-runtime -d "$BUILD/rtsp-service.jar"
java -jar "$BUILD/rtsp-service.jar"
RETRY_NAME=RtspPlaybackPolicy
RETRY_SOURCE="$ROOT/app/src/main/java/com/minhacamera/${RETRY_NAME}.kt"
kotlinc "$RETRY_SOURCE" \
  "$ROOT/app/src/main/java/com/minhacamera/CameraStreamProtocol.kt" \
  "$ROOT/tests/CameraStreamProtocolSmoke.kt" -include-runtime -d "$BUILD/camera-protocols.jar"
java -jar "$BUILD/camera-protocols.jar"

# SETUP/PLAY com desafios Digest simulados em sockets de loopback.
# Compilação isolada impede incompatibilidade de classpath após o JAR maior.
CODEC_NAME=RtspCodec
PROBE_NAME=RtspProbe
kotlinc \
  "$ROOT/tests/protocol-stubs/AndroidStubs.kt" \
  "$ROOT/app/src/main/java/com/minhacamera/${CODEC_NAME}.kt" \
  "$ROOT/app/src/main/java/com/minhacamera/${PROBE_NAME}.kt" \
  "$ROOT/tests/RtspSessionSmoke.kt" \
  -include-runtime -d "$BUILD/rtsp-session.jar"
java -jar "$BUILD/rtsp-session.jar"

# Pinça: regras puras de zoom local/remoto, sem Android e sem acesso a rede.
kotlinc "$ROOT/app/src/main/java/com/minhacamera/PhonePinchZoom.kt" \
  "$ROOT/tests/PhonePinchZoomSmoke.kt" -include-runtime -d "$BUILD/pinch-zoom.jar"
java -jar "$BUILD/pinch-zoom.jar"

# Regressão da troca Activity -> serviço com tela apagada, sem hardware Android.
kotlinc "$ROOT/app/src/main/java/com/minhacamera/PhoneBackgroundHandoffPolicy.kt" \
  "$ROOT/tests/PhoneBackgroundHandoffSmoke.kt" -include-runtime -d "$BUILD/background-handoff.jar"
java -jar "$BUILD/background-handoff.jar"

# FPS realmente publicado pelo transmissor, independente da meta configurada.
kotlinc "$ROOT/app/src/main/java/com/minhacamera/PhonePublishedFps.kt" \
  "$ROOT/tests/PhonePublishedFpsSmoke.kt" -include-runtime -d "$BUILD/published-fps.jar"
java -jar "$BUILD/published-fps.jar"

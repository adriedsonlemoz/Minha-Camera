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

# SETUP/PLAY real em socket local simulado (401 Digest, RTP e 461).
kotlinc -classpath "$BUILD/tests.jar" "$ROOT/tests/RtspSessionSmoke.kt" \
  -include-runtime -d "$BUILD/rtsp-session.jar"
java -cp "$BUILD/tests.jar:$BUILD/rtsp-session.jar" com.minhacamera.RtspSessionSmokeKt

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
  "$ROOT/tests/ProtocolSmoke.kt" \
  "$ROOT/tests/PhoneEcoPolicySmoke.kt" \
  -include-runtime -d "$BUILD/tests.jar"
java -jar "$BUILD/tests.jar"

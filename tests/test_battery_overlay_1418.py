"""Teste de leitura/formatacao isolada no JVM e integracao estatica da marca d'agua."""
from pathlib import Path
import subprocess
import tempfile
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
src = (root / 'app/src/main/java/com/minhacamera/PhoneBatteryOverlay.kt').read_text()
start = src.index('internal data class PhoneBatteryReading(')
end = src.index('/** Uma leitura do broadcast sticky')
reading = src[start:end]
# Extrai funcoes puras de normalizacao sem referencias ao Android.
body_start = src.index('        fun fromValues(')
body_end = src.index('        fun fromIntent(', body_start)
normalizer = src[body_start:body_end]
content = '''import java.util.Locale\n''' + reading + '''\ninternal object TestNormalizer {\n''' + normalizer + '''}\n''' + '''\nfun main() {
    check(TestNormalizer.fromValues(31, 50, 347) == PhoneBatteryReading(62, 347))
    check(TestNormalizer.fromValues(0, 100, 0).levelPercent == 0)
    check(TestNormalizer.fromValues(100, 100, 900).levelPercent == 100)
    check(TestNormalizer.fromValues(-1, 100, null).levelPercent == null)
    check(TestNormalizer.fromValues(101, 100, null).levelPercent == null)
    check(TestNormalizer.fromValues(99, 0, null).levelPercent == null)
    check(TestNormalizer.fromValues(100, 100, -201).temperatureTenthsC == null)
    check(TestNormalizer.fromValues(100, 100, 901).temperatureTenthsC == null)
    check(PhoneBatteryReading(61, 347).display() == "Bateria 61%  |  Bat. 34,7°C")
    check(PhoneBatteryReading(null, null).display() == "Bateria --%  |  Bat. --°C")
    println("OK: 10 testes JVM de bateria e temperatura")
}
'''
with tempfile.TemporaryDirectory() as tmp:
    source = Path(tmp) / 'ReadingSmoke.kt'
    jar = Path(tmp) / 'battery.jar'
    source.write_text(content)
    subprocess.run(['kotlinc', str(source), '-include-runtime', '-d', str(jar)], check=True, timeout=40)
    subprocess.run(['java', '-jar', str(jar)], check=True, timeout=12)
activity = (root / 'app/src/main/java/com/minhacamera/PhoneCameraActivity.kt').read_text()
layout = (root / 'app/src/main/res/layout/activity_phone_camera.xml').read_text()
assert 'batteryOverlay.burnInto(frame, w, h, label)' in activity
assert activity.index('batteryOverlay.burnInto(frame, w, h, label)') < activity.index('compressToJpeg(', activity.index('private fun publishFrame('))
assert activity.index('batteryOverlay.burnInto(frame, w, h, label)') < activity.index('recorder?.append(snapshot, timestamp)')
assert 'phoneTelemetryPreview' in layout
assert 'show_battery_overlay' in activity and 'phone_hide_battery_overlay' in activity
assert 'registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))' in src
assert '15_000L' in src
assert 'EXTRA_TEMPERATURE' in src
assert 'nv21[uv + channel]' in src
assert 'ByteArrayOutputStream' in activity
ET.fromstring(layout)
print('OK: 10 invariantes de overlay MJPEG/MP4/UI e recursos')

"""Smoke JVM e invariantes para a telemetria da bateria e do MJPEG/MP4."""
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
model = (root / 'app/src/main/java/com/minhacamera/PhoneBatteryReading.kt').read_text()
overlay = (root / 'app/src/main/java/com/minhacamera/PhoneBatteryOverlay.kt').read_text()
activity = (root / 'app/src/main/java/com/minhacamera/PhoneCameraActivity.kt').read_text()
layout = (root / 'app/src/main/res/layout/activity_phone_camera.xml').read_text()

start = overlay.index('        fun fromValues(')
end = overlay.index('        fun fromIntent(', start)
normalizer = overlay[start:end]
source_code = model + '''\ninternal object TestNormalizer {\n''' + normalizer + '''}\nfun main() {
    check(TestNormalizer.fromValues(31, 50, 347, false, -220) == PhoneBatteryReading(62, 347, false, -220))
    check(TestNormalizer.fromValues(0, 100, 0, true, 150).levelPercent == 0)
    check(TestNormalizer.fromValues(100, 100, 900, true, null).levelPercent == 100)
    check(TestNormalizer.fromValues(-1, 100, null, null, null).levelPercent == null)
    check(TestNormalizer.fromValues(101, 100, null, null, null).levelPercent == null)
    check(TestNormalizer.fromValues(99, 0, null, null, null).levelPercent == null)
    check(TestNormalizer.fromValues(100, 100, -201, false, null).temperatureTenthsC == null)
    check(TestNormalizer.fromValues(100, 100, 901, false, null).temperatureTenthsC == null)
    check(PhoneBatteryReading(61, 347, true, 160).overlayLabel().contains("61%"))
    check(PhoneBatteryReading(61, 347, true, 160).detailsLabel().contains("carregando"))
    check(PhoneBatteryReading(null, null, null, null).overlayLabel().contains("--%"))
    check(PhoneBatteryReading(77, 390, false, -575).compactLabel() == "🔋 77% · 39,0°C")
    check(PhoneBatteryReading(77, 390, true, 200).compactLabel() == "⚡🔋 77% · 39,0°C")
    check(PhoneBatteryReading(77, 390, false, -575).compactLabel(false, false, true).isEmpty())
    check(PhoneBatteryReading(null, 390, true, null).compactLabel(false, true, true) == "⚡ · 39,0°C")
    println("OK: 15 testes JVM de bateria e telemetria")
}
'''
with tempfile.TemporaryDirectory() as tmp:
    src = Path(tmp) / 'ReadingSmoke.kt'
    jar = Path(tmp) / 'battery.jar'
    src.write_text(source_code)
    subprocess.run(['kotlinc', str(src), '-include-runtime', '-d', str(jar)], check=True, timeout=45)
    subprocess.run(['java', '-jar', str(jar)], check=True, timeout=12)

# v1.4.27: os dados nunca são gravados nos pixels (MJPEG/MP4); evita badge duplicado no PiP.
assert 'batteryOverlay.burnInto(frame, w, h, label)' not in activity
assert 'PhoneBatteryOverlay' not in activity
assert 'battery.compactLabel()' in activity
assert 'phoneTelemetryPreview' in layout
assert 'show_battery_overlay' in activity and 'phone_hide_battery_overlay' in activity
assert 'registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))' in overlay
assert '15_000L' in overlay
assert 'EXTRA_TEMPERATURE' in overlay
assert 'nv21[uv + channel]' in overlay
assert 'ByteArrayOutputStream' in activity
ET.fromstring(layout)
print('OK: telemetria somente na interface, MJPEG/MP4 sem selo gravado')

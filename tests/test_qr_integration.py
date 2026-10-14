#!/usr/bin/env python3
"""Validação estática do pareamento QR; NÃO substitui build Android/teste físico."""
from pathlib import Path
from xml.etree import ElementTree as ET

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main"
main = (src / "java/com/minhacamera/MainActivity.kt").read_text()
transmitter = (src / "java/com/minhacamera/PhoneCameraActivity.kt").read_text()
receiver = (src / "java/com/minhacamera/PhoneCameraViewerActivity.kt").read_text()
url = (src / "java/com/minhacamera/PhoneCameraUrls.kt").read_text()
gradle = (root / "app/build.gradle.kts").read_text()
manifest = ET.parse(src / "AndroidManifest.xml")
strings = ET.parse(src / "res/values/strings.xml")
layout = ET.parse(src / "res/layout/activity_phone_camera.xml")
android = "{http://schemas.android.com/apk/res/android}"
assert 'implementation("com.journeyapps:zxing-android-embedded:4.3.0")' in gradle
assert "registerForActivityResult(ScanContract())" in main
assert "setDesiredBarcodeFormats(ScanOptions.QR_CODE)" in main
assert "PhoneCameraUrls.normalize(text)" in main
assert "startPhoneQrScanner()" in main
assert "choosePhoneRole()" in main and "R.string.phone_role_transmit" in main
assert "PhoneCameraViewerActivity::class.java" in main
assert 'cams.firstOrNull { it.url == stream }' in main
assert "BarcodeEncoder().encodeBitmap(url, BarcodeFormat.QR_CODE" in transmitter
assert "showQrCode(session.url)" in transmitter
assert 'qrCode.visibility = View.GONE' in transmitter
assert 'session.start()' in transmitter
assert 'PhoneCameraUrls.normalize(intent.getStringExtra("url")' in receiver
assert 'private val tokenPattern = Regex("/live/[0-9a-f]{32}")' in url
assert manifest.getroot().find("uses-permission[@" + android + "name='android.permission.CAMERA']") is not None
assert manifest.getroot().find('application').get(android+'hardwareAccelerated') == 'true'
ids={node.get(android+'id') for node in layout.iter() if node.get(android+'id')}
assert '@+id/phoneQrCode' in ids and '@+id/phonePreview' in ids
names = {e.get('name') for e in strings.getroot().findall('string')}
for n in ('phone_role_transmit', 'phone_role_view', 'phone_qr_invalid_message', 'phone_qr_scan_prompt'):
    assert n in names, n
for f in src.rglob('*.xml'):
    ET.parse(f)
print("OK: fluxo de seleção, QR, validação de URL, manifesto, recursos e XML")

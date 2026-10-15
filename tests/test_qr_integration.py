#!/usr/bin/env python3
"""QR local, popups e fluxo de transmissão, validação estrutural sem Android."""
from pathlib import Path
from xml.etree import ElementTree as ET
root = Path(__file__).resolve().parents[1]
src = root / 'app/src/main'
main = (src / 'java/com/minhacamera/MainActivity.kt').read_text()
transmitter = (src / 'java/com/minhacamera/PhoneCameraActivity.kt').read_text()
receiver = (src / 'java/com/minhacamera/PhoneCameraViewerActivity.kt').read_text()
urls = (src / 'java/com/minhacamera/PhoneCameraUrls.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()
a = '{http://schemas.android.com/apk/res/android}'
assert 'implementation("com.journeyapps:zxing-android-embedded:4.3.0")' in gradle
assert 'registerForActivityResult(ActivityResultContracts.RequestPermission())' in main
assert 'barcode.decodeSingle(object : BarcodeCallback' in main
assert 'DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))' in main
assert 'barcode.resume()' in main and 'barcode.pause()' in main
assert 'PhoneCameraUrls.normalize(text)' in main
assert 'startPhoneQrScanner()' in main
assert 'handlePhoneQr(payload)' in main
assert 'PhoneCameraViewerActivity::class.java' in main
assert 'cams.firstOrNull { it.url == stream }' in main
assert 'BarcodeEncoder().encodeBitmap(live, BarcodeFormat.QR_CODE' in transmitter
assert 'openQrDialog()' in transmitter
assert 'showQrCode(session.url)' not in transmitter
assert 'session.start()' in transmitter
assert 'PhoneCameraUrls.normalize(intent.getStringExtra("url")' in receiver
assert 'private val tokenPattern = Regex("/live/[0-9a-f]{32}")' in urls
manifest = ET.parse(src/'AndroidManifest.xml').getroot()
assert manifest.find('uses-permission[@'+a+'name="android.permission.CAMERA"]') is not None
main_activity = next(e for e in manifest.iter('activity') if e.get(a+'name') == '.MainActivity')
assert main_activity.get(a+'screenOrientation') == 'portrait'
for file in src.rglob('*.xml'): ET.parse(file)
print('OK: scanner QR modal, permissão, lifecycle, token, validação URL, Manifest e XML')

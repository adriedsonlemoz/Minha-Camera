#!/usr/bin/env python3
"""Valida ligações de layout e fluxos da interface 1.4.14. Não equivale a build Android."""
from pathlib import Path
from xml.etree import ElementTree as ET
from collections import Counter
import re
root=Path(__file__).resolve().parents[1]
res=root/'app/src/main/res'
src=root/'app/src/main/java/com/minhacamera'
a='{http://schemas.android.com/apk/res/android}'
main=ET.parse(res/'layout/activity_main.xml').getroot()
phone=ET.parse(res/'layout/activity_phone_camera.xml').getroot()
qr=ET.parse(res/'layout/dialog_phone_qr.xml').getroot()
scan=ET.parse(res/'layout/dialog_phone_scan.xml').getroot()
main_ids={node.get(a+'id') for node in main.iter()}
phone_ids={node.get(a+'id') for node in phone.iter()}
for id in ('mainMore','mainTabCameras','mainTabPhone','camerasSection','phoneSection','list','empty','mainScan','mainAdd','roleTransmit','roleAssistir','phoneManual','phoneSavedList','phoneEmpty'):
    assert f'@+id/{id}' in main_ids,id
for id in ('phonePreview','phoneStatus','phoneQr','phoneLink','phoneSwitch','phoneRevoke','previewPlaceholder'):
    assert f'@+id/{id}' in phone_ids,id
assert '@+id/dialogQr' in {e.get(a+'id') for e in qr.iter()}
assert '@+id/phoneScanner' in {e.get(a+'id') for e in scan.iter()}
assert scan.find('.//com.journeyapps.barcodescanner.BarcodeView') is not None
main_kt=(src/'MainActivity.kt').read_text()
phone_kt=(src/'PhoneCameraActivity.kt').read_text()
assert 'supportActionBar?.hide()' in main_kt
assert 'findViewById<View>(R.id.mainMore).setOnClickListener' in main_kt
assert 'showTab(false)' in main_kt and 'showTab(true)' in main_kt
assert 'R.layout.dialog_phone_scan' in main_kt and 'R.layout.dialog_phone_qr' in phone_kt
assert 'android:screenOrientation="portrait"' in (root/'app/src/main/AndroidManifest.xml').read_text()
assert 'findViewById<View>(R.id.phoneLink)' in phone_kt
assert 'getString(R.string.ui_link_copy)' in phone_kt and 'getString(R.string.ui_link_share)' in phone_kt
assert 'PhoneCameraAccess.revoke(this)' in phone_kt
assert 'session.start()' in phone_kt
assert 'CameraStore(this)' in main_kt and 'store.save(cams)' in main_kt
assert 'CameraAdapter(phoneOnly = true)' in main_kt and 'CameraAdapter(phoneOnly = false)' in main_kt
assert (root/'docs/SIGNING_CERT_SHA256.txt').read_text().strip()
strings=ET.parse(res/'values/strings.xml').getroot()
names=[e.get('name') for e in strings.findall('string')]
assert len(names)==len(set(names)),[(k,v) for k,v in Counter(names).items() if v>1]
for f in (root/'app/src/main').rglob('*.xml'): ET.parse(f)
for f in (res/'layout').glob('*.xml'):
    tree=ET.parse(f)
    for el in tree.iter():
        for value in el.attrib.values():
            if value.startswith('@string/'):
                assert value[8:] in names,(f,value)
print('OK: navegação, role, popups, segurança, layouts e recursos XML')

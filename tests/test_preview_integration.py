#!/usr/bin/env python3
"""Prévia e QR sob demanda, checagens estruturais offline."""
from pathlib import Path
from xml.etree import ElementTree as ET
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/minhacamera/PhoneCameraActivity.kt').read_text()
l=ET.parse(r/'app/src/main/res/layout/activity_phone_camera.xml').getroot()
a='{http://schemas.android.com/apk/res/android}'
ids = {node.get(a+'id'): node for node in l.iter() if node.get(a+'id')}
pv=ids['@+id/phonePreview']
assert pv.tag == 'TextureView'
assert '@+id/phoneQr' in ids
assert '@+id/phoneAddress' in ids and ids['@+id/phoneAddress'].get(a+'visibility') == 'gone'
assert 'phoneQrCode' not in s
assert 'showQrCode(session.url)' not in s
assert 'TextureView.SurfaceTextureListener' in s
for required in ('c.setPreviewTexture(preview.surfaceTexture', 'onSurfaceTextureUpdated(',
                 'preview.isAvailable', 'stopStreaming()', 'publishedStreamFrame',
                 'receivedCameraFrames', 'renderedPreviewFrames', 'R.string.phone_no_frames',
                 'status.setText(R.string.phone_online)', 'session.publish(jpeg.toByteArray())',
                 'R.layout.dialog_phone_qr', 'R.id.phoneQr', 'R.id.phoneLink',
                 'R.id.phoneSwitch', 'R.id.phoneRevoke'):
    assert required in s, required
assert 'qrDialog?.dismiss()' in s
assert 'openQrDialog()' in s and 'openLinkDialog()' in s
assert 'placeholder.visibility = View.GONE' in s
for image,view in [((480,640),(636,370)),((640,480),(636,370)),((480,640),(370,636)),((1280,720),(370,636))]:
    iw,ih=image;vw,vh=view;scale=max(vw/iw,vh/ih)
    assert iw*scale >= vw-1e-8 and ih*scale >= vh-1e-8
    assert abs((iw*scale)/(ih*scale)-iw/ih)<1e-10
print('OK: prévia TextureView, controles compactos, QR sob demanda e geometria')

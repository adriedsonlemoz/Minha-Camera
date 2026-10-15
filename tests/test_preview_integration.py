#!/usr/bin/env python3
"""Prévia de câmera: verificação estrutural offline, sem Android/device."""
from pathlib import Path
from xml.etree import ElementTree as ET
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/minhacamera/PhoneCameraActivity.kt').read_text()
l=ET.parse(r/'app/src/main/res/layout/activity_phone_camera.xml').getroot()
a='{http://schemas.android.com/apk/res/android}'
pv=next(n for n in l.iter() if n.get(a+'id')=='@+id/phonePreview')
qr=next(n for n in l.iter() if n.get(a+'id')=='@+id/phoneQrCode')
assert pv.tag=='TextureView',f'A prévia deve ser TextureView: {pv.tag}'
assert list(l.iter()).index(pv) < list(l.iter()).index(qr), 'Prévia deve aparecer antes do QR'
assert 'SurfaceView' not in s.split('class PhoneCameraActivity')[0], 'Sem SurfaceView legado'
for required in ('TextureView.SurfaceTextureListener','preview.surfaceTextureListener = this',
                 'preview.isAvailable','c.setPreviewTexture(preview.surfaceTexture',
                 'onSurfaceTextureAvailable(', 'onSurfaceTextureSizeChanged(',
                 'onSurfaceTextureDestroyed(', 'onSurfaceTextureUpdated(',
                 'stopStreaming()', 'updatePreviewTransform()',
                 'publishedStreamFrame', 'receivedCameraFrames', 'renderedPreviewFrames',
                 'R.string.phone_waiting_frames', 'R.string.phone_no_frames',
                 'R.string.phone_preview_not_rendered'):
    assert required in s,required
before=s.index('c.startPreview()')
after=s.index('showQrCode(session.url)',before)
assert 'status.setText(R.string.phone_online)' not in s[before:after], 'Nao declarar transmissao antes de primeiro JPEG'
assert 'session.publish(jpeg.toByteArray())' in s
assert 'status.setText(R.string.phone_online)' in s
strings=ET.parse(r/'app/src/main/res/values/strings.xml').getroot()
names={e.get('name') for e in strings}
for k in ('phone_preview_label','phone_pairing_label','phone_waiting_frames','phone_no_frames','phone_preview_not_rendered'):
    assert k in names,k
# O fator centerCrop nao pode comprimir nem achatar a proporcao do buffer.
for image,view in [((480,640),(636,370)),((640,480),(636,370)),((480,640),(370,636)),((1280,720),(370,636))]:
    iw,ih=image;vw,vh=view;scale=max(vw/iw,vh/ih)
    assert iw*scale >= vw-1e-8 and ih*scale >= vh-1e-8
    assert abs((iw*scale)/(ih*scale)-iw/ih)<1e-10
print('OK: TextureView, lifecycle, sequência prévia/QR, status após primeiro JPEG, ajuste de proporção e strings')

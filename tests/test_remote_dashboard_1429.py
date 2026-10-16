"""Validação estrutural do novo visualizador e controle Eco autenticado."""
from pathlib import Path
from xml.etree import ElementTree as ET
ROOT = Path(__file__).resolve().parents[1]
A = '{http://schemas.android.com/apk/res/android}'

def test_viewer_layout_controls_are_real():
    root = ET.parse(ROOT/'app/src/main/res/layout/activity_phone_viewer.xml').getroot()
    ids={e.attrib.get(A+'id') for e in root.iter()}
    for control in ('viewerCameraName','viewerBatteryPill','viewerTemperature','phoneViewerPreviewContainer',
        'viewerRecordAction','viewerSnapshotAction','viewerRotateAction','viewerPipAction',
        'viewerRemoteEco','viewerRemoteQualityValue','viewerRemoteFpsValue','phoneViewerMore',
        'phoneViewerPipHost'):
        assert '@+id/'+control in ids

def test_remote_commands_token_based_and_disabled_without_service():
    src=ROOT/'app/src/main/java/com/minhacamera'
    server=(src/'PhoneCameraServer.kt').read_text()
    urls=(src/'PhoneCameraUrls.kt').read_text()
    service=(src/'PhoneCameraBackgroundService.kt').read_text()
    viewer=(src/'PhoneCameraViewerActivity.kt').read_text()
    for keyword in ('POST $control/eco/enable HTTP/1.1', 'POST $control/eco/disable HTTP/1.1',
                    'X-Eco:', '403 Forbidden', 'changeEco?.invoke(ecoAction)'):
        assert keyword in server
    assert 'val live = normalize(input) ?: return null' in urls
    assert 'recordingCommandUrl' in urls and 'ecoCommandUrl' in urls
    assert 'if (!running || closing) false' in service
    assert 'if (remoteEco == null)' in viewer
    assert 'MediaStore.Images.Media.EXTERNAL_CONTENT_URI' in viewer
    assert 'R.string.viewer_photo_saved' in viewer

def test_no_nonfunctional_remote_zoom_or_resolution_controls():
    layout=(ROOT/'app/src/main/res/layout/activity_phone_viewer.xml').read_text()
    assert 'android:text="Resolução"' in layout and 'android:text="FPS"' in layout
    assert 'Zoom remoto' not in layout

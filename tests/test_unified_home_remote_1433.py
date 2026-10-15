from pathlib import Path
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'app/src/main/java/com/minhacamera'
RES=ROOT/'app/src/main/res'

def test_unified_home():
    xml=(RES/'layout/activity_main.xml').read_text()
    assert 'mainMedia' in xml and 'mainMore' in xml
    assert 'mainTabCameras' not in xml and 'mainTabPhone' not in xml
    actions=(RES/'layout/item_camera.xml').read_text()
    for token in ('btnOpen','btnCopy','btnEdit','btnDelete'):
        assert token in actions
    ET.fromstring(xml)

def test_remote_controls_and_failure_reasons():
    server=(SRC/'PhoneCameraServer.kt').read_text()
    activity=(SRC/'PhoneCameraActivity.kt').read_text()
    viewer=(SRC/'PhoneCameraViewerActivity.kt').read_text()
    assert 'X-Control-Reason: $reason' in server
    assert 'recording-active' in server and 'unsupported-mode' in server
    for name in ('changeRecording =','changeEco =','changeFacing =','takePhoto ='):
        assert name in activity
    assert 'remote_blocked_recording' in viewer
    assert 'remote_unsupported_mode' in viewer

def test_media_settings_theme_and_metadata():
    man=(RES.parent/'AndroidManifest.xml').read_text()
    assert '.SettingsActivity' in man and '.MultimediaActivity' in man
    settings=(SRC/'SettingsActivity.kt').read_text()
    assert 'drilemoz@gmail.com' in settings
    assert 'settings_google_unavailable' in settings
    assert 'Google Play Billing' in (RES/'values/strings.xml').read_text()
    assert 'AppCompatDelegate.setDefaultNightMode' in (SRC/'CameraTheme.kt').read_text()
    assert 'X-Device-Model' in (SRC/'PhoneCameraServer.kt').read_text()
    assert 'X-Resolution' in (SRC/'PhoneCameraServer.kt').read_text()
    assert 'refreshDeviceMetadata' in (SRC/'MainActivity.kt').read_text()

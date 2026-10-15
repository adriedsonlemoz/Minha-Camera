"""Validação estrutural 1.4.30: não substitui teste em hardware."""
from pathlib import Path
from xml.etree import ElementTree as ET
R = Path(__file__).resolve().parents[1]
SRC = R/'app/src/main/java/com/minhacamera'


def read(name): return (SRC/name).read_text(encoding='utf-8')


def test_surface_consumes_buffers_offscreen():
    gl = read('PhoneHeadlessPreview.kt')
    assert 'eglCreatePbufferSurface' in gl
    assert 'setOnFrameAvailableListener' in gl
    assert 'frame.updateTexImage()' in gl
    assert 'EGL14.eglMakeCurrent' in gl
    assert 'surfaceTexture.release()' in gl
    service = read('PhoneCameraBackgroundService.kt')
    assert 'PhoneHeadlessPreview.create' in service
    assert 'releasePreview()' in service


def test_manual_transmission_not_auto():
    activity = read('PhoneCameraActivity.kt')
    assert 'private var transmissionEnabled = false' in activity
    assert 'if (!transmissionEnabled)' in activity
    assert 'toggleTransmission()' in activity
    assert 'c.startPreview()' in activity
    assert 'session.start()' in activity


def test_remote_commands_authorized_and_quality_enum():
    server = read('PhoneCameraServer.kt')
    urls = read('PhoneCameraUrls.kt')
    for route in ('/quality/$it', '/fps/$it', '/camera/back', '/camera/front', '/photo'):
        assert route in server
    for endpoint in ('qualityCommandUrl', 'fpsCommandUrl', 'facingCommandUrl', 'photoCommandUrl'):
        assert endpoint in urls
    assert 'quality !in listOf(360, 480, 720, 768, 1080)' in read('PhoneCameraBackgroundService.kt')
    assert 'recording || takingPhoto' in read('PhoneCameraBackgroundService.kt')
    assert 'MAX' not in urls


def test_native_photo_and_private_storage():
    photo = read('PhoneRemotePhoto.kt')
    assert 'MediaStore.Images.Media.EXTERNAL_CONTENT_URI' in photo
    assert 'Environment.DIRECTORY_PICTURES' in photo
    assert 'setPictureSize(photo.width, photo.height)' in read('PhoneCameraBackgroundService.kt')
    assert 'opened.takePicture(' in read('PhoneCameraBackgroundService.kt')


def test_fullscreen_controls_and_welcome_once():
    viewer = read('PhoneCameraViewerActivity.kt')
    main = read('MainActivity.kt')
    assert 'viewerFullscreenControls' in (R/'app/src/main/res/layout/activity_phone_viewer.xml').read_text()
    assert 'fullscreenHost.addView(previewContainer' in viewer
    assert 'phoneViewerFullscreenHost' in viewer
    assert 'last_shown_code' in main and 'versionCode' in main
    assert 'showWelcomeAfterUpdate()' in main
    assert 'X-Recording-Seconds:' in read('PhoneCameraServer.kt')


def test_all_android_xml_wellformed():
    for path in (R/'app/src/main/res').rglob('*.xml'):
        ET.parse(path)

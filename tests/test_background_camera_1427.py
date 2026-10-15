"""Invariantes da transição para câmera foreground e telemetria sem queimar quadro.
Testes estruturais: não substituem execução em Android real.
"""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT/'app/src/main/java/com/minhacamera'
NS = '{http://schemas.android.com/apk/res/android}'

class BackgroundCameraTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.activity = (SRC/'PhoneCameraActivity.kt').read_text()
        cls.service = (SRC/'PhoneCameraBackgroundService.kt').read_text()
        cls.viewer = (SRC/'PhoneCameraViewerActivity.kt').read_text()
        cls.model = (SRC/'PhoneBatteryReading.kt').read_text()
        cls.manifest = ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot()

    def test_camera_fgs_permission_and_service(self):
        permissions = {p.attrib[NS+'name'] for p in self.manifest.findall('uses-permission')}
        for permission in ('android.permission.CAMERA','android.permission.FOREGROUND_SERVICE',
                           'android.permission.FOREGROUND_SERVICE_CAMERA','android.permission.WAKE_LOCK'):
            self.assertIn(permission, permissions)
        service = self.manifest.find("application/service[@"+NS+"name='.PhoneCameraBackgroundService']")
        self.assertEqual(service.attrib[NS+'foregroundServiceType'], 'camera')
        self.assertEqual(service.attrib[NS+'exported'], 'false')
        self.assertEqual(service.attrib[NS+'stopWithTask'], 'false')

    def test_capture_owned_by_service_not_ui(self):
        for token in ('PhoneHeadlessPreview.create(size.width, size.height)', 'opened.setPreviewTexture(preview.surfaceTexture)',
                      'setPreviewCallbackWithBuffer', 'PhoneCameraServer(', 'session.publish(encoded, reading)'):
            self.assertIn(token, self.service)
        self.assertNotIn('preview.surfaceTexture ?: ', self.service)
        self.assertIn('START_NOT_STICKY', self.service)
        self.assertIn('if (!backgroundMode)', self.activity)

    def test_manual_visible_initiation(self):
        self.assertIn('ContextCompat.startForegroundService(context, intent)', self.service)
        self.assertIn('startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)', self.service)
        self.assertIn('private fun toggleBackgroundMode()', self.activity)
        self.assertIn('ACTION_START, selectedFacing', self.activity)
        self.assertNotIn('BOOT_COMPLETED', self.manifest.itertext().__str__())

    def test_record_optional_and_finish(self):
        self.assertIn('private var recorder: PhoneMp4Recorder?', self.service)
        self.assertIn('if (recording && recordingBusy.compareAndSet(false, true))', self.service)
        self.assertIn('ACTION_RECORD', self.service)
        self.assertIn('finishRecording()', self.service)
        self.assertIn('if (foregroundStarted) stopForeground(STOP_FOREGROUND_REMOVE)', self.service)
        self.assertIn('ACTION_STOP', self.service)

    def test_locks_and_cleanup(self):
        self.assertIn('PowerManager.PARTIAL_WAKE_LOCK', self.service)
        self.assertIn('WifiManager.WIFI_MODE_FULL_HIGH_PERF', self.service)
        self.assertIn('wifiLock?.release()', self.service)
        self.assertIn('wakeLock?.release()', self.service)
        self.assertIn('old?.release()', self.service)

    def test_no_jpeg_or_mp4_overlay(self):
        self.assertNotIn('batteryOverlay.burnInto', self.activity)
        self.assertNotIn('burnInto(', self.service)
        self.assertIn('battery.compactLabel()', self.activity)
        self.assertIn('compactLabel(showBatteryLevel, showTemperature, showChargeSymbol)', self.viewer)
        self.assertIn('showChargeSymbol = settings.getBoolean', self.viewer)
        self.assertIn('.setMultiChoiceItems(', self.viewer)

    def test_remote_recording_control_is_authenticated(self):
        server=(SRC/'PhoneCameraServer.kt').read_text()
        urls=(SRC/'PhoneCameraUrls.kt').read_text()
        for token in ('changeRecording?.invoke(recordAction)', 'POST $control/record/start HTTP/1.1',
                      'POST $control/record/stop HTTP/1.1', '403 Forbidden', 'X-Recording:'):
            self.assertIn(token,server)
        self.assertIn('val live = normalize(input) ?: return null',urls)
        self.assertIn('PhoneCameraUrls.recordingCommandUrl',self.viewer)
        self.assertIn('connection.requestMethod = "POST"',self.viewer)

    def test_manifest_resource_and_version(self):
        self.assertIn('versionCode = 42', (ROOT/'app/build.gradle.kts').read_text())
        self.assertIn('versionName = "1.4.32"', (ROOT/'app/build.gradle.kts').read_text())
        strs=ET.parse(ROOT/'app/src/main/res/values/strings.xml').getroot()
        names={n.attrib['name'] for n in strs.findall('string')}
        for name in ('phone_background_enable','phone_background_stop','phone_view_telemetry_options'):
            self.assertIn(name,names)

if __name__ == '__main__': unittest.main()

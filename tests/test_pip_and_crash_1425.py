"""Verificações estruturais e regressões locais de PiP/crash 1.4.25.
Não substitui instrumentação Android nem Logcat físico.
"""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'app/src/main/java/com/minhacamera'
RES = ROOT / 'app/src/main/res'
A = '{http://schemas.android.com/apk/res/android}'

class PiPAndCrashTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest = ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot()
        cls.layout = ET.parse(RES/'layout/activity_phone_viewer.xml').getroot()
        cls.viewer = (SRC/'PhoneCameraViewerActivity.kt').read_text()
        cls.player = (SRC/'PlayerActivity.kt').read_text()
        cls.pip = (SRC/'PipSupport.kt').read_text()
        cls.diag = (SRC/'CameraApplication.kt').read_text()
        cls.main = (SRC/'MainActivity.kt').read_text()

    def test_manifest_pip_only_receivers(self):
        activities = {a.attrib[A+'name']: a for a in self.manifest.findall('application/activity')}
        for name in ['.PhoneCameraViewerActivity', '.PlayerActivity']:
            self.assertEqual(activities[name].attrib.get(A+'supportsPictureInPicture'), 'true')
            self.assertIn('screenSize', activities[name].attrib[A+'configChanges'])
        self.assertIsNone(activities['.PhoneCameraActivity'].attrib.get(A+'supportsPictureInPicture'))
        perms = [a.attrib[A+'name'] for a in self.manifest.findall('uses-permission')]
        self.assertNotIn('android.permission.SYSTEM_ALERT_WINDOW', perms)

    def test_activity_lifecycle_never_stops_on_pause(self):
        self.assertNotIn('override fun onPause() { stop()', self.viewer)
        self.assertIn('override fun onStop()', self.viewer)
        self.assertIn('if (!active) start()', self.viewer)
        self.assertIn('if (android.os.Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode)', self.player)
        self.assertIn('override fun onDestroy() { stop(); super.onDestroy() }', self.viewer)

    def test_pip_previews_and_restore(self):
        ids = {e.attrib.get(A+'id') for e in self.layout.iter()}
        self.assertIn('@+id/phoneViewerPipHost', ids)
        self.assertIn('@+id/phoneViewerScroll', ids)
        self.assertIn('normalPreviewParent.addView(previewContainer, previewIndex,', self.viewer)
        self.assertIn('pipHost.addView(previewContainer', self.viewer)
        self.assertIn('getString(R.string.pip_window)', self.viewer)
        self.assertIn('findViewById<View>(R.id.btnPip).setOnClickListener { enterPip() }', self.player)
        self.assertIn('if (recordingSession != null || recordPending)', self.player)
        self.assertIn('setAspectRatio(ratio)', self.pip)
        self.assertIn('PackageManager.FEATURE_PICTURE_IN_PICTURE', self.pip)
        self.assertIn('Build.VERSION.SDK_INT >= Build.VERSION_CODES.O', self.pip)

    def test_crash_reports_minimal_and_private(self):
        self.assertIn('android:name=".CameraApplication"', (ROOT/'app/src/main/AndroidManifest.xml').read_text())
        self.assertIn('Thread.setDefaultUncaughtExceptionHandler', self.diag)
        self.assertIn('getSharedPreferences("private_crash_diagnostics"', self.diag)
        self.assertNotIn('append(cause.message)', self.diag)
        self.assertIn('showPreviousCrashIfAny()', self.main)
        self.assertIn('clipboard.setPrimaryClip', self.main)
        self.assertIn('catch (error: LinkageError)', self.player)

    def test_versioned_and_strings_declared(self):
        text = (ROOT/'app/build.gradle.kts').read_text()
        self.assertIn('versionCode = 41', text)
        self.assertIn('versionName = "1.4.31"', text)
        root = ET.parse(RES/'values/strings.xml').getroot()
        names = {x.attrib['name'] for x in root.findall('string')}
        for key in ('pip_window', 'pip_unavailable', 'pip_wait_for_video', 'pip_stop_recording',
                    'crash_diagnostic_copy', 'crash_diagnostic_title', 'crash_diagnostic_copied'):
            self.assertIn(key, names)

if __name__=='__main__':
    unittest.main()

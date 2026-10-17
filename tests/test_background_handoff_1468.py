import unittest
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
CODE = ROOT / 'app/src/main/java/com/minhacamera'

class TestBackgroundHandoff(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.activity = (CODE/'PhoneCameraActivity.kt').read_text()
        cls.service = (CODE/'PhoneCameraBackgroundService.kt').read_text()
        cls.policy = (CODE/'PhoneBackgroundHandoffPolicy.kt').read_text()
    def test_no_14_second_forced_local_restart(self):
        self.assertNotIn('backgroundRequestedAt < 14_000L', self.activity)
        self.assertIn('PhoneBackgroundHandoffPolicy.evaluate(', self.activity)
        self.assertIn('resumePreviewAfterBackgroundRelease()', self.activity)
    def test_camera_never_reopened_during_service_transition(self):
        self.assertIn('PhoneCameraBackgroundService.starting || PhoneCameraBackgroundService.releasing', self.activity)
        self.assertIn('PhoneBackgroundHandoffPolicy.canResumeLocal(', self.activity)
    def test_service_distinguishes_starting_and_frame_published(self):
        for snippet in ('starting = true', 'starting = false', 'firstFramePublished = true',
                        'startupPhase = "aguardando primeiro quadro"',
                        'PhoneStreamDiagnostics.add("Tela apagada: primeiro quadro publicado")'):
            self.assertIn(snippet, self.service)
    def test_error_does_not_steal_camera_before_release(self):
        self.assertIn('if (service.starting || service.running || service.releasing)', self.activity)
        self.assertIn('PhoneCameraBackgroundService.ACTION_STOP', self.activity)
        self.assertIn('releasing = true', self.service)
        self.assertIn('releasing = false', self.service)
    def test_startup_timeout_and_first_frame_are_independent(self):
        self.assertIn('START_TIMEOUT_MS = 30_000L', self.policy)
        self.assertIn('FIRST_FRAME_TIMEOUT_MS = 45_000L', self.policy)
        self.assertIn('Decision.WAIT_FRAMES', self.policy)
    def test_gradle_runs_new_smoke_with_protocol_tests(self):
        build=(ROOT/'build.gradle.kts').read_text()
        self.assertIn('tasks.register<JavaExec>("testBackgroundHandoff")', build)
        self.assertIn('tasks.named("testProtocol") { dependsOn("testBackgroundHandoff") }', build)

    def test_diagnostics_include_handoff_phase(self):
        for snippet in ('iniciando=${PhoneCameraBackgroundService.starting}',
                        'primeiroQuadro=${PhoneCameraBackgroundService.firstFramePublished}'):
            self.assertIn(snippet, self.activity)

if __name__=='__main__': unittest.main()

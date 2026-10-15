"""Contrato estático da versão 1.4.32 (não substitui codec Android real)."""
import xml.etree.ElementTree as ET
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'app/src/main/java/com/minhacamera'

class H264AndDiagnosticsTests(unittest.TestCase):
    def test_transport_fallback_and_access(self):
        server=(SRC/'PhoneCameraServer.kt').read_text()
        urls=(SRC/'PhoneCameraUrls.kt').read_text()
        self.assertIn('Content-Type: video/h264',server)
        self.assertIn('POST $control/codec/h264',server)
        self.assertIn('POST $control/codec/mjpeg',server)
        self.assertIn('GET $control/status',server)
        self.assertIn('GET $control/diagnostic',server)
        self.assertIn('if (codecStatus?.invoke() != "h264")',server)
        self.assertIn('fun h264Url(',urls)
        self.assertIn('normalize(input)',urls)
    def test_avc_hardware_and_fallback(self):
        encoder=(SRC/'PhoneH264LiveEncoder.kt').read_text()
        service=(SRC/'PhoneCameraBackgroundService.kt').read_text()
        self.assertIn('MediaCodec.createByCodecName',encoder)
        self.assertIn('it.isHardwareAccelerated',encoder)
        self.assertIn('PhoneYuv420.convert',encoder)
        self.assertIn('liveCodec = "mjpeg"',service)
        self.assertIn('PhoneStreamDiagnostics.add(',service)
    def test_ui_and_clock(self):
        activity=(SRC/'PhoneCameraActivity.kt').read_text()
        viewer=(SRC/'PhoneCameraViewerActivity.kt').read_text()
        self.assertIn('SystemClock.elapsedRealtime()',activity)
        self.assertIn('PhoneCameraBackgroundService.transmissionStartedMs',activity)
        self.assertIn('viewerCodec = "h264"',viewer)
        self.assertIn('demux=h264',viewer)
        self.assertIn('startH264(',viewer)
        for xml in ['activity_phone_camera.xml','activity_phone_viewer.xml','dialog_phone_more.xml']:
            ET.parse(ROOT/'app/src/main/res/layout'/xml)
    def test_diagnostics_include_egl_and_persistence(self):
        logs=(SRC/'PhoneStreamDiagnostics.kt').read_text()
        egl=(SRC/'PhoneHeadlessPreview.kt').read_text()
        self.assertIn('MAX_EVENTS = 120',logs)
        self.assertIn('private_stream_diagnostics',logs)
        self.assertIn('drainedFrames',egl)
        self.assertIn('eglErrors',egl)
        self.assertIn('updateTexImage()',egl)
if __name__=='__main__': unittest.main()

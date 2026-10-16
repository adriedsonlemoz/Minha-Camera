"""Gesto de zoom deve preservar toque simples, limite de envio e status autenticado."""
from pathlib import Path
import unittest
P=Path(__file__).resolve().parents[1]/'app/src/main/java/com/minhacamera'

class PinchZoom60(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.viewer=(P/'PhoneCameraViewerActivity.kt').read_text()
        cls.math=(P/'PhonePinchZoom.kt').read_text()
    def test_scale_gesture_and_single_tap(self):
        self.assertIn('ScaleGestureDetector',self.viewer)
        self.assertIn('MotionEvent.ACTION_POINTER_DOWN -> {',self.viewer)
        self.assertIn('if (!pinchTouched) previewContainer.performClick()',self.viewer)
        self.assertIn('image.setOnTouchListener',self.viewer)
        self.assertIn('requestDisallowInterceptTouchEvent(true)',self.viewer)
        self.assertIn('view.setOnTouchListener',self.viewer)
    def test_send_throttled_single_flight_authenticated(self):
        for phrase in ['gestureZoomInFlight', '230L - elapsed', 'gestureZoomPending',
                       'PhoneCameraUrls.zoomCommandUrl', 'conn.requestMethod = "POST"',
                       'NetUtil.bindToWifi(applicationContext)']:
            self.assertIn(phrase,self.viewer)
        self.assertIn('if (isFinishing || isDestroyed)',self.viewer)
    def test_limits_and_local_fallback(self):
        self.assertIn('(startPercent + delta).coerceIn(0, 100)',self.math)
        self.assertIn('(startFactor * scale).coerceIn(1f, 3f)',self.math)
        self.assertIn('if (viewerControlsLocked || isPipLayout) return false',self.viewer)
    def test_no_frame_reencoding(self):
        self.assertNotIn('BitmapFactory',self.math)
        self.assertIn('localZoomFactor = PhonePinchZoom.localFactor',self.viewer)

if __name__=='__main__': unittest.main()

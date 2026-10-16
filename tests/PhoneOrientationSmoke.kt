package com.minhacamera

fun checkOrientationModes() {
    val m = PhoneOrientationMode
    check(m.deviceDegrees(m.PORTRAIT, 90) == 0)
    check(m.deviceDegrees(m.LANDSCAPE, 0) == 90)
    check(m.deviceDegrees(m.AUTO, 270) == 270)
    check(m.deviceDegrees("unknown", 90) == 90)
    check(m.frameDegrees(90, false, 0) == 90)
    check(m.frameDegrees(90, false, 90) == 180)
    check(m.frameDegrees(90, true, 0) == 90)
    check(m.frameDegrees(90, true, 90) == 0)
    check(m.fromDisplayDegrees(0) == 0)
    check(m.fromDisplayDegrees(90) == 270)
    check(m.fromDisplayDegrees(180) == 180)
    check(m.fromDisplayDegrees(270) == 90)
    check(m.deviceDegrees(m.LANDSCAPE, 90) == 90)
    check(m.deviceDegrees(m.LANDSCAPE, 270) == 270)
    check(m.deviceDegrees(m.PORTRAIT, 180) == 180)
    for (sensorMount in listOf(0, 90, 180, 270)) {
        for (device in listOf(0, 90, 180, 270)) {
            // OrientationEventListener retorna ângulo horário, Camera.Parameters.setRotation também.
            check(m.frameDegrees(sensorMount, false, device) == (sensorMount + device) % 360)
            check(m.frameDegrees(sensorMount, true, device) == (sensorMount - device + 360) % 360)
        }
    }
    check(m.snap(359) == 0)
    check(m.snap(92) == 90)
    check(m.snap(-1) == -1)
    val tracker = PhoneOrientationStabilizer(800)
    check(tracker.observe(3, 1000) == null)
    check(tracker.observe(1, 1799) == null)
    check(tracker.observe(2, 1800) == 0)
    check(tracker.observe(95, 1810) == null)
    check(tracker.observe(100, 2300) == null)
    check(tracker.observe(95, 2610) == 90)
    check(tracker.observe(91, 3000) == null)
    println("OK: orientação AUTO/vertical/horizontal e estabilização de 800ms")
}

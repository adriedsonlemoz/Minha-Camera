package com.minhacamera

fun main() {
    check(PhonePinchZoom.remotePercent(0, 1f) == 0)
    check(PhonePinchZoom.remotePercent(50, 2f) == 100)
    check(PhonePinchZoom.remotePercent(80, .5f) == 0)
    check(PhonePinchZoom.remotePercent(10, .5f) == 0)
    check(PhonePinchZoom.remotePercent(50, 0f) == 50)
    check(PhonePinchZoom.remotePercent(50, Float.NaN) == 50)
    check(PhonePinchZoom.localFactor(1f, 2f) == 2f)
    check(PhonePinchZoom.localFactor(1f, .5f) == 1f)
    check(PhonePinchZoom.localFactor(3f, 4f) == 3f)
    println("PhonePinchZoom: limites, continuidade, invalidos e fallback OK")
}

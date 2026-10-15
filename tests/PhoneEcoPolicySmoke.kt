package com.minhacamera

/** Smoke tests JVM puro, sem dispositivo Android. */
internal fun checkPhoneEcoPolicy() {
    val p = PhoneEcoPolicy
    check(p.intervalMs(false, PhoneHeatState.NORMAL) == 90L)
    check(p.intervalMs(true, PhoneHeatState.NORMAL) == 200L)
    check(p.intervalMs(false, PhoneHeatState.HOT) == 300L)
    check(p.intervalMs(true, PhoneHeatState.HOT) == 300L)
    check(p.jpegQuality(false, PhoneHeatState.NORMAL) == 72)
    check(p.jpegQuality(true, PhoneHeatState.NORMAL) == 50)
    check(p.jpegQuality(false, PhoneHeatState.HOT) == 45)
    check(p.dualPreviewIntervalMs(false, PhoneHeatState.NORMAL) == 160L)
    check(p.dualPreviewIntervalMs(true, PhoneHeatState.NORMAL) == 500L)
    check(p.dualPreviewIntervalMs(false, PhoneHeatState.HOT) == 1000L)
    var state = PhoneHeatState.NORMAL
    state = p.heatState(419, state)
    check(state == PhoneHeatState.NORMAL)
    state = p.heatState(420, state)
    check(state == PhoneHeatState.WARM)
    state = p.heatState(450, state)
    check(state == PhoneHeatState.HOT)
    state = p.heatState(430, state)
    check(state == PhoneHeatState.HOT) // hysteresis
    state = p.heatState(419, state)
    check(state == PhoneHeatState.WARM)
    state = p.heatState(400, state)
    check(state == PhoneHeatState.WARM)
    state = p.heatState(399, state)
    check(state == PhoneHeatState.NORMAL)
    check(p.heatState(null, PhoneHeatState.HOT) == PhoneHeatState.NORMAL)
    check(p.heatState(1000, PhoneHeatState.WARM) == PhoneHeatState.NORMAL)
    println("OK: policy Eco, termômetro da bateria e histerese (18 casos)")
}

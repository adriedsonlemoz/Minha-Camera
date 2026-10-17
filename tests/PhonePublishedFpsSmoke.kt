package com.minhacamera

fun main() {
    val fps = PhonePublishedFps()
    check(fps.measured(1000L) == 0)
    for (i in 0..20) fps.mark(1000L + i * 100L)
    check(fps.measured(3050L) in 9..11) // ~10 quadros por segundo
    check(fps.measured(8000L) == 0) // não repetir valor antigo se o encoder congelou
    println("PhonePublishedFps: meta != quadros publicados, estagnação, janela OK")
}

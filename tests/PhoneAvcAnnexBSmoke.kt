package com.minhacamera

internal fun checkAvcAnnexB() {
    val annex = byteArrayOf(0, 0, 0, 1, 0x67, 0x11)
    check(PhoneAvcAnnexB.normalize(annex).contentEquals(annex))
    val packet = byteArrayOf(0, 0, 0, 2, 0x67, 0x11, 0, 0, 0, 2, 0x68, 0x22)
    val expected = byteArrayOf(0, 0, 0, 1, 0x67, 0x11, 0, 0, 0, 1, 0x68, 0x22)
    check(PhoneAvcAnnexB.normalize(packet).contentEquals(expected))
    val corrupt = byteArrayOf(0, 0, 0, 100, 1, 2)
    check(PhoneAvcAnnexB.normalize(corrupt).contentEquals(corrupt))
    check(PhoneAvcAnnexB.normalize(byteArrayOf()).isEmpty())
    println("OK: H.264 Annex-B: NALs diretas, comprimento AVCC, corrupção e entrada vazia")
}

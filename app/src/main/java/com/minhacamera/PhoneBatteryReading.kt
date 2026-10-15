package com.minhacamera

import java.util.Locale

/** Valores de bateria/temperatura/corrente pertencem ao celular TRANSMISSOR. */
internal data class PhoneBatteryReading(
    val levelPercent: Int?,
    val temperatureTenthsC: Int?,
    val charging: Boolean?,
    val averageCurrentMaToday: Int?
) {
    private fun levelText() = levelPercent?.let { "$it%" } ?: "--%"
    private fun tempText() = temperatureTenthsC?.let {
        String.format(Locale.forLanguageTag("pt-BR"), "%.1f°C", it / 10.0)
    } ?: "--°C"
    private fun chargeText() = when (charging) {
        true -> "carregando"
        false -> "sem carga"
        null -> "status --"
    }
    private fun avgText() = averageCurrentMaToday?.let { if (it > 0) "+${it} mA" else "$it mA" } ?: "--"

    /** Selo compacto para gravar diretamente no vídeo transmitido. */
    fun overlayLabel(): String = "${levelText()} · ${chargeText()} · ${tempText()} · média ${avgText()}"

    /** Linha rica para o receptor. */
    fun detailsLabel(): String = "Bateria ${levelText()} · ${chargeText()} · ${tempText()} · consumo médio hoje ${avgText()}"
}


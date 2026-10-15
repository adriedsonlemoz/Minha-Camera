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

    /** Exibido como View no receptor/local, sem gravar nos pixels do vídeo. */
    fun compactLabel(battery: Boolean = true, temperature: Boolean = true, charge: Boolean = true): String {
        val parts = mutableListOf<String>()
        if (battery && levelPercent != null) {
            val bolt = if (charge && charging == true) "⚡" else ""
            parts.add("${bolt}🔋 ${levelPercent}%")
        } else if (charge && charging == true) parts.add("⚡")
        if (temperature && temperatureTenthsC != null) parts.add(tempText())
        return parts.joinToString(" · ")
    }

    /** Etiqueta completa legada, não aplicada aos pixels MJPEG/MP4 nesta versão. */
    fun overlayLabel(): String = "${levelText()} · ${chargeText()} · ${tempText()} · média ${avgText()}"

    /** Linha rica para o receptor. */
    fun detailsLabel(): String = "Bateria ${levelText()} · ${chargeText()} · ${tempText()} · consumo médio hoje ${avgText()}"
}


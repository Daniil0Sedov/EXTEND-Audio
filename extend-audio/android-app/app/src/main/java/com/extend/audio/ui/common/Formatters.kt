package com.extend.audio.ui.common

/** Вспомогательные форматтеры для времени трека и краткого отображения настроек EQ. */

import com.extend.audio.domain.model.EqDefaults
import com.extend.audio.domain.model.EqSetting

/** Форматирует длительность трека из миллисекунд в привычный вид `мм:сс`. */
fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}

/** Форматирует усиление полосы с сохранением знака `+` для положительных значений. */
fun formatBandGain(gain: Int): String {
    return if (gain > 0) "+$gain" else gain.toString()
}

/** Собирает краткую строку со всеми полосами пресета для списков и карточек. */
fun formatBandsSummary(bands: List<EqSetting>): String {
    return EqDefaults.normalizeBands(bands).joinToString(separator = " • ") { setting ->
        "${setting.bandLabel} ${formatBandGain(setting.gain)}"
    }
}

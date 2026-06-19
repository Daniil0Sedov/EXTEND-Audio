package com.extend.audio.ui.common

import com.extend.audio.domain.model.EqDefaults
import com.extend.audio.domain.model.EqSetting

fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}

fun formatBandGain(gain: Int): String {
    return if (gain > 0) "+$gain" else gain.toString()
}

fun formatBandsSummary(bands: List<EqSetting>): String {
    return EqDefaults.normalizeBands(bands).joinToString(separator = " • ") { setting ->
        "${setting.bandLabel} ${formatBandGain(setting.gain)}"
    }
}

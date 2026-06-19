package com.extend.audio.domain.model

import kotlin.math.abs
import kotlin.math.roundToInt

object EqDefaults {
    private val defaultBandDefinitions: List<BandDefinition> = listOf(
        BandDefinition("60 Hz", 60f),
        BandDefinition("230 Hz", 230f),
        BandDefinition("910 Hz", 910f),
        BandDefinition("3.6 kHz", 3_600f),
        BandDefinition("14 kHz", 14_000f),
    )

    val defaultBandLabels: List<String> = defaultBandDefinitions.map { definition -> definition.label }

    fun defaultBands(): List<EqSetting> {
        return defaultBandDefinitions.map { definition -> EqSetting(definition.label, 0) }
    }

    fun normalizeBands(input: List<EqSetting>): List<EqSetting> {
        if (input.isEmpty()) return defaultBands()

        val byLabel = input.associateBy { it.bandLabel.trim().lowercase() }
        val groupedByNearestBand = input
            .mapNotNull { setting ->
                parseFrequencyHz(setting.bandLabel)?.let { frequencyHz ->
                    ParsedBand(
                        frequencyHz = frequencyHz,
                        gain = setting.gain.coerceIn(-12, 12),
                    )
                }
            }
            .groupBy { parsedBand ->
                defaultBandDefinitions.indices.minByOrNull { index ->
                    abs(defaultBandDefinitions[index].frequencyHz - parsedBand.frequencyHz)
                } ?: 0
            }

        return defaultBandDefinitions.mapIndexed { index, definition ->
            val exactMatch = byLabel[definition.label.lowercase()]
            if (exactMatch != null) {
                EqSetting(definition.label, exactMatch.gain.coerceIn(-12, 12))
            } else {
                val mappedGains = groupedByNearestBand[index].orEmpty().map { parsedBand -> parsedBand.gain }
                EqSetting(
                    bandLabel = definition.label,
                    gain = if (mappedGains.isEmpty()) {
                        0
                    } else {
                        mappedGains.average().roundToInt().coerceIn(-12, 12)
                    },
                )
            }
        }
    }

    fun parseFrequencyHz(label: String): Float? {
        val normalized = label.trim().lowercase().replace(",", ".")
        return when {
            normalized.contains("khz") -> {
                normalized.substringBefore("khz").trim().toFloatOrNull()?.times(1_000f)
            }

            normalized.contains("hz") -> {
                normalized.substringBefore("hz").trim().toFloatOrNull()
            }

            else -> null
        }
    }

    private data class BandDefinition(
        val label: String,
        val frequencyHz: Float,
    )

    private data class ParsedBand(
        val frequencyHz: Float,
        val gain: Int,
    )
}

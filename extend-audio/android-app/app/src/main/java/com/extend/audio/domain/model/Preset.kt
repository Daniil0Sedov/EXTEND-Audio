package com.extend.audio.domain.model

/** Доменная модель пользовательского пресета под конкретную модель наушников. */

data class Preset(
    val id: String,
    val name: String,
    val headphoneModel: String,
    val bands: List<EqSetting>,
)

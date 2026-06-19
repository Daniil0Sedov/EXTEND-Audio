package com.extend.audio.domain.model

data class Preset(
    val id: String,
    val name: String,
    val headphoneModel: String,
    val bands: List<EqSetting>,
)

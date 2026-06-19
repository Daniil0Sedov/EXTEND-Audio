package com.extend.audio.domain.model

/** Доменная модель одной полосы эквалайзера: частота и усиление в дБ. */

data class EqSetting(
    val bandLabel: String,
    val gain: Int,
)

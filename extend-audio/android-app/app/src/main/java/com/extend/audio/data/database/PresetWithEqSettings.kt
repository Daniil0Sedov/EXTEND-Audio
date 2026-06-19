package com.extend.audio.data.database

import androidx.room.Embedded
import androidx.room.Relation

data class PresetWithEqSettings(
    @Embedded
    val preset: PresetEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "preset_id",
        entity = EqSettingEntity::class,
    )
    val eqSettings: List<EqSettingEntity>,
)

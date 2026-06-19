package com.extend.audio.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "eq_settings",
    primaryKeys = ["preset_id", "band_index"],
    foreignKeys = [
        ForeignKey(
            entity = PresetEntity::class,
            parentColumns = ["id"],
            childColumns = ["preset_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("preset_id")]
)
data class EqSettingEntity(
    @ColumnInfo(name = "preset_id")
    val presetId: String,
    @ColumnInfo(name = "band_index")
    val bandIndex: Int,
    @ColumnInfo(name = "band_label")
    val bandLabel: String,
    val gain: Int,
)

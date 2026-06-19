package com.extend.audio.data.database

/** Сущность пресета в Room без вложенных полос эквалайзера. */

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @ColumnInfo(name = "headphone_model")
    val headphoneModel: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)

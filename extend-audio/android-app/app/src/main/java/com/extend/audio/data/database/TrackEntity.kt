package com.extend.audio.data.database

/** Сущность трека в БД: метаданные файла, длительность и путь к обложке. */

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val uri: String,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    val artist: String?,
    @ColumnInfo(name = "artwork_uri")
    val artworkUri: String?,
    @ColumnInfo(name = "added_at")
    val addedAt: Long,
)

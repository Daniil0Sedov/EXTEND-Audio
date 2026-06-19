package com.extend.audio.domain.model

/** Доменная модель локального аудиотрека, импортированного пользователем в библиотеку. */

data class Track(
    val id: String,
    val title: String,
    val uri: String,
    val durationMs: Long,
    val artist: String? = null,
    val artworkUri: String? = null,
)

package com.extend.audio

/** Точка инициализации приложения: здесь создаются общие зависимости для всего MVP. */

import android.app.Application
import androidx.media3.session.MediaSession
import androidx.room.Room
import com.extend.audio.data.database.AppDatabase
import com.extend.audio.data.local.RoomAudioRepository
import com.extend.audio.domain.repository.AudioRepository
import com.extend.audio.player.PlayerController

class ExtendAudioApplication : Application() {

    private val database: AppDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "extend-audio.db"
        )
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
    }

    val audioRepository: AudioRepository by lazy {
        RoomAudioRepository(
            trackDao = database.trackDao(),
            presetDao = database.presetDao(),
            eqSettingDao = database.eqSettingDao(),
        )
    }

    val playerController: PlayerController by lazy {
        PlayerController(this)
    }

    val mediaSession: MediaSession by lazy {
        MediaSession.Builder(this, playerController.exoPlayer).build()
    }

    override fun onTerminate() {
        mediaSession.release()
        playerController.release()
        super.onTerminate()
    }
}

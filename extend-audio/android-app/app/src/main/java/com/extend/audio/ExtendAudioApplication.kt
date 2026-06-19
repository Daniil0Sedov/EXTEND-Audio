package com.extend.audio

import android.app.Application
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

    override fun onTerminate() {
        playerController.release()
        super.onTerminate()
    }
}

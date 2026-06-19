package com.extend.audio.data.database

/** Главная Room-база, где локально хранятся треки, пресеты и полосы эквалайзера. */

import androidx.room.migration.Migration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TrackEntity::class, PresetEntity::class, EqSettingEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun presetDao(): PresetDao
    abstract fun eqSettingDao(): EqSettingDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE tracks ADD COLUMN artwork_uri TEXT"
                )
            }
        }
    }
}

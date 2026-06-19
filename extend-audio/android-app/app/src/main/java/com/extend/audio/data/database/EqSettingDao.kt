package com.extend.audio.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EqSettingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(settings: List<EqSettingEntity>)

    @Query("DELETE FROM eq_settings WHERE preset_id = :presetId")
    suspend fun deleteByPresetId(presetId: String)
}

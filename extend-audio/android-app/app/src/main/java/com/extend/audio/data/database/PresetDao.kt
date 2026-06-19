package com.extend.audio.data.database

/** DAO для списка пресетов и операций выбора активного пресета. */

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetDao {
    @Transaction
    @Query("SELECT * FROM presets ORDER BY created_at DESC")
    fun observeAllWithEqSettings(): Flow<List<PresetWithEqSettings>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preset: PresetEntity)

    @Query("DELETE FROM presets WHERE id = :presetId")
    suspend fun deleteById(presetId: String)
}

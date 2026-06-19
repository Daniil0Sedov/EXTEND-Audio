package com.extend.audio.domain.repository

/** Контракт доступа к трекам, пресетам и текущему состоянию аудиосессии. */

import com.extend.audio.domain.model.Preset
import com.extend.audio.domain.model.Track
import kotlinx.coroutines.flow.StateFlow

interface AudioRepository {
    val tracks: StateFlow<List<Track>>
    val presets: StateFlow<List<Preset>>
    val currentTrack: StateFlow<Track?>
    val activePreset: StateFlow<Preset?>
    val abTestEnabled: StateFlow<Boolean>

    suspend fun importTrack(track: Track)
    suspend fun importTracks(tracks: List<Track>)
    suspend fun selectTrack(trackId: String)
    suspend fun applyPreset(presetId: String)
    suspend fun createDraftPreset()
    suspend fun ensureActivePreset()
    suspend fun editPreset(presetId: String)
    suspend fun updateActivePresetName(name: String)
    suspend fun updateActivePresetModel(model: String)
    suspend fun updateActiveBand(index: Int, gain: Int)
    suspend fun saveActivePreset()
    suspend fun deletePreset(presetId: String)
    suspend fun setAbTestEnabled(enabled: Boolean)
}

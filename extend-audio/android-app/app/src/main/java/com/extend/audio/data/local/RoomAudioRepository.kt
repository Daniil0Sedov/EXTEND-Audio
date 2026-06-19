package com.extend.audio.data.local

/** Реализация репозитория поверх Room для офлайн-работы с треками и пресетами. */

import com.extend.audio.data.database.EqSettingDao
import com.extend.audio.data.database.EqSettingEntity
import com.extend.audio.data.database.PresetDao
import com.extend.audio.data.database.PresetEntity
import com.extend.audio.data.database.PresetWithEqSettings
import com.extend.audio.data.database.TrackDao
import com.extend.audio.data.database.TrackEntity
import com.extend.audio.domain.model.EqDefaults
import com.extend.audio.domain.model.EqSetting
import com.extend.audio.domain.model.Preset
import com.extend.audio.domain.model.Track
import com.extend.audio.domain.repository.AudioRepository
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class RoomAudioRepository(
    private val trackDao: TrackDao,
    private val presetDao: PresetDao,
    private val eqSettingDao: EqSettingDao,
) : AudioRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _currentTrack = MutableStateFlow<Track?>(null)
    private val _activePreset = MutableStateFlow<Preset?>(null)
    private val _abTestEnabled = MutableStateFlow(false)

    override val tracks: StateFlow<List<Track>> = trackDao.observeAll()
        .map { entities -> entities.map { it.toDomain() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val presets: StateFlow<List<Preset>> = presetDao.observeAllWithEqSettings()
        .map { items -> items.map { it.toDomain() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()
    override val activePreset: StateFlow<Preset?> = _activePreset.asStateFlow()
    override val abTestEnabled: StateFlow<Boolean> = _abTestEnabled.asStateFlow()

    override suspend fun importTrack(track: Track) {
        trackDao.upsert(track.toEntity())
        if (_currentTrack.value?.id == track.id) {
            _currentTrack.value = track
        }
    }

    override suspend fun importTracks(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        trackDao.upsertAll(tracks.map { it.toEntity() })
        val currentId = _currentTrack.value?.id
        if (currentId != null) {
            tracks.firstOrNull { it.id == currentId }?.let { updatedTrack ->
                _currentTrack.value = updatedTrack
            }
        }
    }

    override suspend fun selectTrack(trackId: String) {
        _currentTrack.value = tracks.value.firstOrNull { it.id == trackId }
    }

    override suspend fun applyPreset(presetId: String) {
        _activePreset.value = presets.value.firstOrNull { it.id == presetId }?.copy()
    }

    override suspend fun createDraftPreset() {
        _activePreset.value = blankPreset()
    }

    override suspend fun ensureActivePreset() {
        if (_activePreset.value == null) {
            _activePreset.value = blankPreset()
        }
    }

    override suspend fun editPreset(presetId: String) {
        _activePreset.value = presets.value.firstOrNull { it.id == presetId }?.copy()
            ?: blankPreset()
    }

    override suspend fun updateActivePresetName(name: String) {
        _activePreset.update { preset ->
            (preset ?: blankPreset()).copy(name = name)
        }
    }

    override suspend fun updateActivePresetModel(model: String) {
        _activePreset.update { preset ->
            (preset ?: blankPreset()).copy(headphoneModel = model)
        }
    }

    override suspend fun updateActiveBand(index: Int, gain: Int) {
        _activePreset.update { preset ->
            val currentPreset = preset ?: blankPreset()
            currentPreset.copy(
                bands = currentPreset.bands.mapIndexed { bandIndex, setting ->
                    if (bandIndex == index) setting.copy(gain = gain.coerceIn(-12, 12)) else setting
                }
            )
        }
    }

    override suspend fun saveActivePreset() {
        val preset = _activePreset.value ?: return
        val persistedPreset = if (preset.id.isBlank()) {
            preset.copy(id = UUID.randomUUID().toString())
        } else {
            preset
        }

        presetDao.upsert(
            PresetEntity(
                id = persistedPreset.id,
                name = persistedPreset.name,
                headphoneModel = persistedPreset.headphoneModel,
                createdAt = System.currentTimeMillis(),
            )
        )
        eqSettingDao.deleteByPresetId(persistedPreset.id)
        eqSettingDao.insertAll(
            persistedPreset.bands.mapIndexed { index, setting ->
                EqSettingEntity(
                    presetId = persistedPreset.id,
                    bandIndex = index,
                    bandLabel = setting.bandLabel,
                    gain = setting.gain,
                )
            }
        )
        _activePreset.value = persistedPreset
    }

    override suspend fun deletePreset(presetId: String) {
        presetDao.deleteById(presetId)
        if (_activePreset.value?.id == presetId) {
            _activePreset.value = null
        }
    }

    override suspend fun setAbTestEnabled(enabled: Boolean) {
        _abTestEnabled.value = enabled
    }

    private fun blankPreset(): Preset {
        return Preset(
            id = "",
            name = "",
            headphoneModel = "",
            bands = EqDefaults.defaultBands(),
        )
    }

    private fun TrackEntity.toDomain(): Track {
        return Track(
            id = id,
            title = title,
            uri = uri,
            durationMs = durationMs,
            artist = artist,
            artworkUri = artworkUri,
        )
    }

    private fun Track.toEntity(): TrackEntity {
        return TrackEntity(
            id = id,
            title = title,
            uri = uri,
            durationMs = durationMs,
            artist = artist,
            artworkUri = artworkUri,
            addedAt = System.currentTimeMillis(),
        )
    }

    private fun PresetWithEqSettings.toDomain(): Preset {
        return Preset(
            id = preset.id,
            name = preset.name,
            headphoneModel = preset.headphoneModel,
            bands = EqDefaults.normalizeBands(
                eqSettings
                    .sortedBy { it.bandIndex }
                    .map { setting -> EqSetting(setting.bandLabel, setting.gain) }
            ),
        )
    }
}

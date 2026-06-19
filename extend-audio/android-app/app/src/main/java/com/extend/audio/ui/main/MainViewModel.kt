package com.extend.audio.ui.main

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.extend.audio.ExtendAudioApplication
import com.extend.audio.domain.model.Preset
import com.extend.audio.domain.model.Track
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class MainUiState(
    val tracks: List<Track> = emptyList(),
    val presets: List<Preset> = emptyList(),
    val currentTrack: Track? = null,
    val activePreset: Preset? = null,
    val abTestEnabled: Boolean = false,
    val isPlaying: Boolean = false,
    val playbackPositionMs: Long = 0L,
    val playbackDurationMs: Long = 0L,
    val isEqualizerAvailable: Boolean = false,
    val isShuffleEnabled: Boolean = false,
    val isRepeatOneEnabled: Boolean = false,
    val visualizerLevel: Float = 0f,
    val visualizerBands: List<Float> = emptyList(),
)

data class FolderImportResult(
    val importedCount: Int = 0,
    val supportedFilesCount: Int = 0,
    val updatedCount: Int = 0,
)

enum class SavePresetResult {
    CREATED,
    UPDATED,
    MISSING_NAME,
    MISSING_MODEL,
    MISSING_NAME_AND_MODEL,
    NO_DRAFT,
}

enum class DeletePresetResult {
    DELETED,
    NOT_FOUND,
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ExtendAudioApplication
    private val repository = app.audioRepository
    private val playerController = app.playerController

    private val shuffleEnabled = MutableStateFlow(false)
    private val repeatOneEnabled = MutableStateFlow(false)

    val uiState: StateFlow<MainUiState> = combine(
        combine(
            repository.tracks,
            repository.presets,
            repository.currentTrack,
            repository.activePreset,
            repository.abTestEnabled,
        ) { tracks, presets, currentTrack, activePreset, abTestEnabled ->
            MainUiState(
                tracks = tracks,
                presets = presets,
                currentTrack = currentTrack,
                activePreset = activePreset,
                abTestEnabled = abTestEnabled,
            )
        },
        combine(
            combine(
                playerController.isPlaying,
                playerController.positionMs,
                playerController.durationMs,
                playerController.isEqualizerAvailable,
            ) { isPlaying, positionMs, durationMs, isEqualizerAvailable ->
                PlaybackBaseUiState(
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isEqualizerAvailable = isEqualizerAvailable,
                )
            },
            playerController.visualizerLevel,
            playerController.visualizerBands,
        ) { playbackBaseState, visualizerLevel, visualizerBands ->
            PlaybackUiState(
                isPlaying = playbackBaseState.isPlaying,
                positionMs = playbackBaseState.positionMs,
                durationMs = playbackBaseState.durationMs,
                isEqualizerAvailable = playbackBaseState.isEqualizerAvailable,
                visualizerLevel = visualizerLevel,
                visualizerBands = visualizerBands,
            )
        },
        shuffleEnabled.asStateFlow(),
        repeatOneEnabled.asStateFlow(),
    ) { baseState, playbackState, isShuffleEnabled, isRepeatOneEnabled ->
        baseState.copy(
            isPlaying = playbackState.isPlaying,
            playbackPositionMs = playbackState.positionMs,
            playbackDurationMs = playbackState.durationMs,
            isEqualizerAvailable = playbackState.isEqualizerAvailable,
            isShuffleEnabled = isShuffleEnabled,
            isRepeatOneEnabled = isRepeatOneEnabled,
            visualizerLevel = playbackState.visualizerLevel,
            visualizerBands = playbackState.visualizerBands,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(
            tracks = repository.tracks.value,
            presets = repository.presets.value,
            currentTrack = repository.currentTrack.value,
            activePreset = repository.activePreset.value,
            abTestEnabled = repository.abTestEnabled.value,
            isPlaying = playerController.isPlaying.value,
            playbackPositionMs = playerController.positionMs.value,
            playbackDurationMs = playerController.durationMs.value,
            isEqualizerAvailable = playerController.isEqualizerAvailable.value,
            isShuffleEnabled = shuffleEnabled.value,
            isRepeatOneEnabled = repeatOneEnabled.value,
            visualizerLevel = playerController.visualizerLevel.value,
            visualizerBands = playerController.visualizerBands.value,
        )
    )

    init {
        viewModelScope.launch {
            repository.activePreset
                .map { preset -> preset?.bands ?: emptyList() }
                .distinctUntilChanged()
                .collectLatest { bands ->
                    playerController.setEqualizerSettings(bands)
                }
        }
    }

    suspend fun importFolder(treeUri: Uri): FolderImportResult = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(getApplication(), treeUri)
            ?: return@withContext FolderImportResult()

        val supportedFiles = mutableListOf<AudioImportCandidate>()
        collectAudioFiles(root, supportedFiles)

        val existingTracksByUri = repository.tracks.value.associateBy { it.uri }
        val tracksToUpsert = mutableListOf<Track>()
        var importedCount = 0
        var updatedCount = 0

        supportedFiles.forEach { candidate ->
            val existingTrack = existingTracksByUri[candidate.audioFile.uri.toString()]
            val builtTrack = buildTrackFromFile(
                file = candidate.audioFile,
                artworkUri = candidate.artworkUri,
                existingTrack = existingTrack,
            ) ?: return@forEach

            if (existingTrack == null) {
                importedCount += 1
                tracksToUpsert += builtTrack
            } else if (existingTrack != builtTrack) {
                updatedCount += 1
                tracksToUpsert += builtTrack
            }
        }

        repository.importTracks(tracksToUpsert)
        FolderImportResult(
            importedCount = importedCount,
            supportedFilesCount = supportedFiles.size,
            updatedCount = updatedCount,
        )
    }

    fun openTrack(track: Track) {
        viewModelScope.launch {
            val shouldRestart = !playerController.hasTrackLoaded(track.id)
            repository.selectTrack(track.id)
            playerController.playTrack(track, restart = shouldRestart)
            if (track.artworkUri.isNullOrBlank()) {
                refreshTrackArtwork(track)
            }
        }
    }

    fun togglePlayback() {
        val track = uiState.value.currentTrack ?: return
        if (playerController.hasTrackLoaded(track.id)) {
            playerController.togglePlayback()
        } else {
            playerController.playTrack(track, restart = false)
        }
    }

    fun seekTo(positionMs: Long) {
        playerController.seekTo(positionMs)
    }

    fun playPrevious() {
        playAdjacentTrack(offset = -1)
    }

    fun playNext() {
        playAdjacentTrack(offset = 1)
    }

    fun toggleShuffle() {
        shuffleEnabled.value = !shuffleEnabled.value
    }

    fun toggleRepeatOne() {
        val enabled = !repeatOneEnabled.value
        repeatOneEnabled.value = enabled
        playerController.setRepeatOneEnabled(enabled)
    }

    fun applyPreset(presetId: String) {
        viewModelScope.launch {
            repository.applyPreset(presetId)
        }
    }

    fun createNewPresetDraft() {
        viewModelScope.launch {
            repository.createDraftPreset()
        }
    }

    suspend fun prepareNewPresetDraft() {
        repository.createDraftPreset()
    }

    fun ensureEditablePreset() {
        viewModelScope.launch {
            repository.ensureActivePreset()
        }
    }

    fun editPreset(presetId: String) {
        viewModelScope.launch {
            repository.editPreset(presetId)
        }
    }

    suspend fun preparePresetForEditing(presetId: String) {
        repository.editPreset(presetId)
    }

    fun updatePresetName(name: String) {
        viewModelScope.launch {
            repository.updateActivePresetName(name)
        }
    }

    fun updatePresetModel(model: String) {
        viewModelScope.launch {
            repository.updateActivePresetModel(model)
        }
    }

    fun updateBand(index: Int, gain: Int) {
        viewModelScope.launch {
            repository.updateActiveBand(index, gain)
        }
    }

    fun applyCurrentMix() {
        viewModelScope.launch {
            repository.ensureActivePreset()
            playerController.setEqualizerSettings(repository.activePreset.value?.bands ?: emptyList())
        }
    }

    suspend fun savePreset(): SavePresetResult {
        val preset = uiState.value.activePreset ?: return SavePresetResult.NO_DRAFT
        val hasName = preset.name.isNotBlank()
        val hasModel = preset.headphoneModel.isNotBlank()

        if (!hasName && !hasModel) return SavePresetResult.MISSING_NAME_AND_MODEL
        if (!hasName) return SavePresetResult.MISSING_NAME
        if (!hasModel) return SavePresetResult.MISSING_MODEL

        val isNewPreset = preset.id.isBlank()
        repository.saveActivePreset()
        return if (isNewPreset) SavePresetResult.CREATED else SavePresetResult.UPDATED
    }

    suspend fun deletePreset(presetId: String): DeletePresetResult {
        val presetExists = uiState.value.presets.any { it.id == presetId }
        if (!presetExists) return DeletePresetResult.NOT_FOUND
        repository.deletePreset(presetId)
        return DeletePresetResult.DELETED
    }

    fun setAbTestEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAbTestEnabled(enabled)
        }
    }

    fun setAudioVisualizationEnabled(enabled: Boolean, permissionGranted: Boolean) {
        playerController.setVisualizationEnabled(enabled, permissionGranted)
    }

    fun refreshCurrentTrackArtworkIfMissing() {
        val track = uiState.value.currentTrack ?: return
        if (track.artworkUri.isNullOrBlank()) {
            viewModelScope.launch {
                refreshTrackArtwork(track)
            }
        }
    }

    private fun playAdjacentTrack(offset: Int) {
        val state = uiState.value
        val currentTrack = state.currentTrack ?: return
        if (state.tracks.size <= 1) return

        if (state.isShuffleEnabled) {
            val randomTrack = state.tracks
                .filterNot { it.id == currentTrack.id }
                .let { candidates -> candidates[Random.nextInt(candidates.size)] }
            openTrack(randomTrack)
            return
        }

        val currentIndex = state.tracks.indexOfFirst { it.id == currentTrack.id }
        if (currentIndex == -1) return

        val nextIndex = currentIndex + offset
        if (nextIndex !in state.tracks.indices) return

        openTrack(state.tracks[nextIndex])
    }

    private fun collectAudioFiles(root: DocumentFile, collector: MutableList<AudioImportCandidate>) {
        if (!root.isDirectory) {
            if (root.isFile && root.isSupportedAudioFile()) {
                collector += AudioImportCandidate(root, artworkUri = null)
            }
            return
        }

        val children = root.listFiles().toList()
        val imageFiles = children.filter { child -> child.isFile && child.isSupportedArtworkFile() }
        val genericArtwork = imageFiles.firstOrNull { image ->
            val normalizedName = image.name
                ?.substringBeforeLast('.', "")
                ?.trim()
                ?.lowercase()
                .orEmpty()
            normalizedName in GENERIC_ARTWORK_NAMES
        } ?: imageFiles.singleOrNull()

        children.forEach { child ->
            when {
                child.isFile && child.isSupportedAudioFile() -> {
                    val matchingArtwork = findArtworkForAudio(child, imageFiles) ?: genericArtwork
                    collector += AudioImportCandidate(
                        audioFile = child,
                        artworkUri = matchingArtwork?.uri?.toString(),
                    )
                }

                child.isDirectory -> collectAudioFiles(child, collector)
            }
        }
    }

    private fun findArtworkForAudio(
        audioFile: DocumentFile,
        imageFiles: List<DocumentFile>,
    ): DocumentFile? {
        val audioBaseName = audioFile.name
            ?.substringBeforeLast('.', "")
            ?.trim()
            ?.lowercase()
            .orEmpty()

        return imageFiles.firstOrNull { image ->
            image.name
                ?.substringBeforeLast('.', "")
                ?.trim()
                ?.lowercase() == audioBaseName
        }
    }

    private fun buildTrackFromFile(
        file: DocumentFile,
        artworkUri: String?,
        existingTrack: Track? = null,
    ): Track? {
        val uri = file.uri
        val contentResolver = getApplication<Application>().contentResolver
        val displayName = contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(getApplication(), uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: displayName?.substringBeforeLast('.')
                ?: "Imported track"
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            val trackId = existingTrack?.id ?: UUID.randomUUID().toString()
            val persistedArtworkUri = artworkUri
                ?.let { source -> persistArtworkLocally(Uri.parse(source), trackId) }
                ?: existingTrack?.artworkUri

            Track(
                id = trackId,
                title = title,
                uri = uri.toString(),
                durationMs = durationMs,
                artist = artist,
                artworkUri = persistedArtworkUri,
            )
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private suspend fun refreshTrackArtwork(track: Track) = withContext(Dispatchers.IO) {
        val artworkSourceUri = findArtworkForExistingTrack(track) ?: return@withContext
        val persistedArtworkUri = persistArtworkLocally(artworkSourceUri, track.id) ?: return@withContext
        val updatedTrack = track.copy(artworkUri = persistedArtworkUri)
        repository.importTrack(updatedTrack)
        repository.selectTrack(updatedTrack.id)
    }

    private fun findArtworkForExistingTrack(track: Track): Uri? {
        val audioUri = runCatching { Uri.parse(track.uri) }.getOrNull() ?: return null
        val authority = audioUri.authority ?: return null
        val documentId = runCatching { android.provider.DocumentsContract.getDocumentId(audioUri) }
            .getOrNull() ?: return null
        val parentDocumentId = documentId.substringBeforeLast('/', missingDelimiterValue = "")
        if (parentDocumentId.isBlank()) return null

        val parentTreeUri =
            android.provider.DocumentsContract.buildTreeDocumentUri(authority, parentDocumentId)
        val parentFolder = DocumentFile.fromTreeUri(getApplication(), parentTreeUri) ?: return null
        val imageFiles = parentFolder.listFiles().filter { it.isFile && it.isSupportedArtworkFile() }
        val genericArtwork = imageFiles.firstOrNull { image ->
            val normalizedName = image.name
                ?.substringBeforeLast('.', "")
                ?.trim()
                ?.lowercase()
                .orEmpty()
            normalizedName in GENERIC_ARTWORK_NAMES
        } ?: imageFiles.singleOrNull()

        return findArtworkForTrackMetadata(track, imageFiles)?.uri
            ?: genericArtwork?.uri
    }

    private fun findArtworkForTrackMetadata(
        track: Track,
        imageFiles: List<DocumentFile>,
    ): DocumentFile? {
        val audioBaseName = track.uri.substringAfterLast('/').substringBeforeLast('.')
            .normalizeArtworkKey()
        val titleKey = track.title.normalizeArtworkKey()
        val artistKey = track.artist.orEmpty().normalizeArtworkKey()

        return imageFiles.firstOrNull { image ->
            image.name?.substringBeforeLast('.', "")?.normalizeArtworkKey() == audioBaseName
        } ?: imageFiles.firstOrNull { image ->
            image.name?.substringBeforeLast('.', "")?.normalizeArtworkKey() == titleKey
        } ?: imageFiles.firstOrNull { image ->
            val imageKey = image.name?.substringBeforeLast('.', "")?.normalizeArtworkKey().orEmpty()
            titleKey.isNotBlank() && imageKey.contains(titleKey)
        } ?: imageFiles.firstOrNull { image ->
            val imageKey = image.name?.substringBeforeLast('.', "")?.normalizeArtworkKey().orEmpty()
            titleKey.isNotBlank() &&
                artistKey.isNotBlank() &&
                imageKey.contains(titleKey) &&
                imageKey.contains(artistKey)
        }
    }

    private fun persistArtworkLocally(sourceUri: Uri, trackId: String): String? {
        val contentResolver = getApplication<Application>().contentResolver
        val extensionFromName = contentResolver.query(
            sourceUri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)?.substringAfterLast('.', "")
            } else {
                null
            }
        }
        val extension = extensionFromName
            ?.lowercase()
            ?.takeIf { it in SUPPORTED_ARTWORK_EXTENSIONS }
            ?: when (contentResolver.getType(sourceUri)) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }

        val artworkDir = File(getApplication<Application>().filesDir, "artwork").apply {
            mkdirs()
        }
        val targetFile = File(artworkDir, "$trackId.$extension")

        return runCatching {
            contentResolver.openInputStream(sourceUri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            targetFile.toUri().toString()
        }.getOrNull()
    }

    private fun DocumentFile.isSupportedAudioFile(): Boolean {
        val audioMime = type?.startsWith("audio/") == true
        if (audioMime) return true

        val extension = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return extension in SUPPORTED_AUDIO_EXTENSIONS
    }

    private fun DocumentFile.isSupportedArtworkFile(): Boolean {
        val imageMime = type?.startsWith("image/") == true
        if (imageMime) return true

        val extension = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return extension in SUPPORTED_ARTWORK_EXTENSIONS
    }

    private fun String.normalizeArtworkKey(): String {
        return lowercase()
            .replace("&", "and")
            .replace(Regex("[^a-z0-9а-я]"), "")
    }

    companion object {
        private val SUPPORTED_AUDIO_EXTENSIONS = setOf(
            "mp3",
            "wav",
            "flac",
            "aac",
            "m4a",
            "ogg",
            "opus",
        )
        private val SUPPORTED_ARTWORK_EXTENSIONS = setOf(
            "png",
            "jpg",
            "jpeg",
            "webp",
        )
        private val GENERIC_ARTWORK_NAMES = setOf(
            "cover",
            "folder",
            "front",
            "artwork",
            "album",
            "thumb",
        )
    }

    private data class PlaybackUiState(
        val isPlaying: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        val isEqualizerAvailable: Boolean,
        val visualizerLevel: Float,
        val visualizerBands: List<Float>,
    )

    private data class PlaybackBaseUiState(
        val isPlaying: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        val isEqualizerAvailable: Boolean,
    )

    private data class AudioImportCandidate(
        val audioFile: DocumentFile,
        val artworkUri: String?,
    )
}

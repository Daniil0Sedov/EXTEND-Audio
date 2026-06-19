package com.extend.audio.player

import android.annotation.SuppressLint
import android.content.Context
import android.media.audiofx.Equalizer
import android.media.audiofx.Visualizer
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.extend.audio.domain.model.EqDefaults
import com.extend.audio.domain.model.EqSetting
import com.extend.audio.domain.model.Track
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerController(context: Context) {

    private val player = ExoPlayer.Builder(context).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var equalizer: Equalizer? = null
    private var visualizer: Visualizer? = null
    private var equalizerAudioSessionId: Int? = null
    private var visualizerAudioSessionId: Int? = null
    private var pendingEqSettings: List<EqSetting> = emptyList()
    private var loadedTrackId: String? = null
    private var visualizationEnabled: Boolean = false
    private var visualizationPermissionGranted: Boolean = false

    private val _isPlaying = MutableStateFlow(false)
    private val _positionMs = MutableStateFlow(0L)
    private val _durationMs = MutableStateFlow(0L)
    private val _isEqualizerAvailable = MutableStateFlow(false)
    private val _visualizerLevel = MutableStateFlow(0f)
    private val _visualizerBands = MutableStateFlow(List(VISUALIZER_SEGMENT_COUNT) { 0f })

    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()
    val isEqualizerAvailable: StateFlow<Boolean> = _isEqualizerAvailable.asStateFlow()
    val visualizerLevel: StateFlow<Float> = _visualizerLevel.asStateFlow()
    val visualizerBands: StateFlow<List<Float>> = _visualizerBands.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            updateProgress()
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            attachEqualizer(audioSessionId)
            attachVisualizer(audioSessionId)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updateProgress()
            if (playbackState == Player.STATE_ENDED) {
                _isPlaying.value = false
                resetVisualizerData()
            }
        }
    }

    init {
        player.addListener(listener)

        scope.launch {
            while (isActive) {
                updateProgress()
                if (!player.isPlaying && _visualizerLevel.value > 0f) {
                    decayVisualizerData()
                }
                delay(180)
            }
        }
    }

    fun hasTrackLoaded(trackId: String?): Boolean = trackId != null && loadedTrackId == trackId

    fun playTrack(track: Track, restart: Boolean = true) {
        if (loadedTrackId == track.id && !restart) {
            player.play()
            updateProgress()
            return
        }

        if (loadedTrackId == track.id && restart) {
            player.seekTo(0)
            player.play()
            updateProgress()
            return
        }

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(track.uri))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .build()
            )
            .build()

        loadedTrackId = track.id
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
        attachEqualizer(player.audioSessionId)
        attachVisualizer(player.audioSessionId)
        updateProgress()
    }

    fun togglePlayback() {
        if (player.isPlaying) {
            player.pause()
        } else if (player.mediaItemCount > 0) {
            player.play()
        }
        updateProgress()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
        updateProgress()
    }

    fun setRepeatOneEnabled(enabled: Boolean) {
        player.repeatMode = if (enabled) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_OFF
        }
    }

    fun setVisualizationEnabled(enabled: Boolean, permissionGranted: Boolean) {
        visualizationEnabled = enabled
        visualizationPermissionGranted = permissionGranted

        if (enabled && permissionGranted) {
            attachVisualizer(player.audioSessionId)
        } else {
            releaseVisualizer()
            resetVisualizerData()
        }
    }

    fun setEqualizerSettings(settings: List<EqSetting>) {
        pendingEqSettings = settings
        applyEqualizerSettings()
    }

    fun release() {
        player.removeListener(listener)
        releaseEqualizer()
        releaseVisualizer()
        player.release()
        scope.cancel()
    }

    private fun updateProgress() {
        _positionMs.value = player.currentPosition.coerceAtLeast(0L)
        _durationMs.value = player.duration.takeIf { it > 0L } ?: 0L
        _isPlaying.value = player.isPlaying
    }

    private fun attachEqualizer(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        if (equalizerAudioSessionId == audioSessionId && equalizer != null) {
            applyEqualizerSettings()
            return
        }

        releaseEqualizer()

        val createdEqualizer = try {
            Equalizer(0, audioSessionId).apply {
                enabled = true
            }
        } catch (_: Throwable) {
            null
        }

        equalizer = createdEqualizer
        equalizerAudioSessionId = createdEqualizer?.let { audioSessionId }
        _isEqualizerAvailable.value = createdEqualizer != null
        applyEqualizerSettings()
    }

    @SuppressLint("MissingPermission")
    private fun attachVisualizer(audioSessionId: Int) {
        if (!visualizationEnabled || !visualizationPermissionGranted || audioSessionId <= 0) {
            releaseVisualizer()
            resetVisualizerData()
            return
        }

        if (visualizerAudioSessionId == audioSessionId && visualizer != null) return

        releaseVisualizer()

        val createdVisualizer = try {
            Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(512)
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int,
                        ) = Unit

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int,
                        ) {
                            updateVisualizerBands(fft)
                        }
                    },
                    (Visualizer.getMaxCaptureRate() / 4).coerceAtLeast(10_000),
                    false,
                    true,
                )
                enabled = true
            }
        } catch (_: Throwable) {
            null
        }

        visualizer = createdVisualizer
        visualizerAudioSessionId = createdVisualizer?.let { audioSessionId }
        if (createdVisualizer == null) {
            resetVisualizerData()
        }
    }

    private fun applyEqualizerSettings() {
        val activeEqualizer = equalizer ?: return

        try {
            val normalizedSettings = EqDefaults.normalizeBands(pendingEqSettings)
            val bandRange = activeEqualizer.bandLevelRange
            val minLevel = bandRange[0].toInt()
            val maxLevel = bandRange[1].toInt()
            val targetBandLevels = mutableMapOf<Short, MutableList<Int>>()
            val deviceBands = (0 until activeEqualizer.numberOfBands.toInt()).map { bandIndex ->
                val band = bandIndex.toShort()
                val centerFrequencyHz = activeEqualizer.getCenterFreq(band) / 1_000f
                band to centerFrequencyHz
            }

            for (band in 0 until activeEqualizer.numberOfBands.toInt()) {
                activeEqualizer.setBandLevel(band.toShort(), 0)
            }

            if (normalizedSettings.isEmpty() || deviceBands.isEmpty()) {
                activeEqualizer.enabled = false
                return
            }

            activeEqualizer.enabled = true
            normalizedSettings.forEach { setting ->
                val targetFrequencyHz = EqDefaults.parseFrequencyHz(setting.bandLabel) ?: return@forEach
                val targetBand = deviceBands.minByOrNull { (_, centerFrequencyHz) ->
                    abs(centerFrequencyHz - targetFrequencyHz)
                }?.first ?: return@forEach
                val targetLevel = (setting.gain * 100).coerceIn(minLevel, maxLevel)
                targetBandLevels.getOrPut(targetBand) { mutableListOf() } += targetLevel
            }

            targetBandLevels.forEach { (band, levels) ->
                val averagedLevel = levels.average().toInt().coerceIn(minLevel, maxLevel)
                activeEqualizer.setBandLevel(band, averagedLevel.toShort())
            }
        } catch (_: Throwable) {
            releaseEqualizer()
        }
    }

    private fun updateVisualizerBands(fft: ByteArray?) {
        if (fft == null || fft.size < 4) return

        val binsPerSegment = IntArray(VISUALIZER_SEGMENT_COUNT)
        val magnitudeSums = FloatArray(VISUALIZER_SEGMENT_COUNT)
        val availableBins = (fft.size / 2) - 1
        if (availableBins <= 0) return

        for (bin in 1..availableBins) {
            val real = fft[bin * 2].toInt()
            val imaginary = fft[(bin * 2) + 1].toInt()
            val magnitude = sqrt((real * real + imaginary * imaginary).toDouble()).toFloat()
            val normalized = (magnitude / 96f).coerceIn(0f, 1f)
            val segmentIndex = (((bin - 1).toFloat() / availableBins) * VISUALIZER_SEGMENT_COUNT)
                .toInt()
                .coerceIn(0, VISUALIZER_SEGMENT_COUNT - 1)

            binsPerSegment[segmentIndex] += 1
            magnitudeSums[segmentIndex] += normalized
        }

        val previousBands = _visualizerBands.value
        val updatedBands = List(VISUALIZER_SEGMENT_COUNT) { index ->
            val average = if (binsPerSegment[index] == 0) 0f else magnitudeSums[index] / binsPerSegment[index]
            val emphasized = (average * average * 1.45f).coerceIn(0f, 1f)
            val previous = previousBands.getOrElse(index) { 0f }
            (previous * 0.32f + emphasized * 0.68f).coerceIn(0f, 1f)
        }

        _visualizerBands.value = updatedBands
        val peakLevel = updatedBands.maxOrNull() ?: 0f
        val bassLevel = updatedBands.take(6).average().toFloat().coerceIn(0f, 1f)
        val transientLevel = maxOf(peakLevel, bassLevel * 1.2f).coerceIn(0f, 1f)
        _visualizerLevel.value = maxOf(
            transientLevel,
            (_visualizerLevel.value * 0.24f) + (transientLevel * 0.76f),
        ).coerceIn(0f, 1f)
    }

    private fun decayVisualizerData() {
        val decayedBands = _visualizerBands.value.map { value ->
            (value * 0.8f).takeIf { it > 0.012f } ?: 0f
        }
        _visualizerBands.value = decayedBands
        _visualizerLevel.value = (_visualizerLevel.value * 0.76f).takeIf { it > 0.02f } ?: 0f
    }

    private fun releaseEqualizer() {
        try {
            equalizer?.release()
        } catch (_: Throwable) {
            // Ignore release failures for unsupported devices.
        }
        equalizer = null
        equalizerAudioSessionId = null
        _isEqualizerAvailable.value = false
    }

    private fun releaseVisualizer() {
        try {
            visualizer?.release()
        } catch (_: Throwable) {
            // Ignore release failures for devices with flaky Visualizer implementation.
        }
        visualizer = null
        visualizerAudioSessionId = null
    }

    private fun resetVisualizerData() {
        _visualizerLevel.value = 0f
        _visualizerBands.value = List(VISUALIZER_SEGMENT_COUNT) { 0f }
    }

    private companion object {
        const val VISUALIZER_SEGMENT_COUNT = 24
    }
}

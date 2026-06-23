package com.extend.audio.player

/** Обёртка над ExoPlayer, Equalizer и Visualizer для управления воспроизведением. */

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.audiofx.Equalizer
import android.media.audiofx.Visualizer
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.extend.audio.domain.model.EqDefaults
import com.extend.audio.domain.model.EqSetting
import com.extend.audio.domain.model.Track
import java.io.File
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

/** Контроллер воспроизведения, который инкапсулирует работу ExoPlayer и аудиоэффектов. */
class PlayerController(context: Context) {

    private val appContext = context.applicationContext
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
    private var onPreviousRequested: (() -> Unit)? = null
    private var onNextRequested: (() -> Unit)? = null
    private var onTrackChanged: ((String) -> Unit)? = null

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
    val exoPlayer: ExoPlayer get() = player

    /** Слушает жизненный цикл плеера и синхронизирует публичное состояние для UI. */
    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            updateProgress()
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            attachEqualizer(audioSessionId)
            attachVisualizer(audioSessionId)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val mediaId = mediaItem?.mediaId
            if (mediaId.isNullOrBlank()) return
            loadedTrackId = mediaId
            onTrackChanged?.invoke(mediaId)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updateProgress()
            if (playbackState == Player.STATE_ENDED) {
                _isPlaying.value = false
                resetVisualizerData()
            }
        }
    }

    /** Запускает фоновое обновление прогресса и плавное затухание visualizer при паузе. */
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

    /** Проверяет, загружен ли уже этот трек в ExoPlayer. */
    fun hasTrackLoaded(trackId: String?): Boolean = trackId != null && loadedTrackId == trackId

    /** Загружает трек в плеер или переиспользует текущий media item при повторном выборе. */
    fun playTrack(
        track: Track,
        queue: List<Track> = listOf(track),
        restart: Boolean = true,
    ) {
        ensurePlaybackServiceStarted()

        // Не пересоздаём media item, если это тот же самый трек: так мы не
        // сбрасываем позицию без необходимости и избегаем лишней подготовки плеера.
        if (loadedTrackId == track.id && !restart && isSameQueue(queue)) {
            player.play()
            updateProgress()
            return
        }

        if (loadedTrackId == track.id && restart && isSameQueue(queue)) {
            player.seekTo(0)
            player.play()
            updateProgress()
            return
        }

        val items = queue.map { queueTrack ->
            MediaItem.Builder()
                .setMediaId(queueTrack.id)
                .setUri(Uri.parse(queueTrack.uri))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(queueTrack.title)
                        .setArtist(queueTrack.artist)
                        .setArtworkData(
                            loadArtworkBytes(queueTrack.artworkUri),
                            MediaMetadata.PICTURE_TYPE_FRONT_COVER,
                        )
                        .build()
                )
                .build()
        }
        val targetIndex = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        loadedTrackId = track.id
        player.setMediaItems(items, targetIndex, C.TIME_UNSET)
        player.prepare()
        player.playWhenReady = true
        attachEqualizer(player.audioSessionId)
        attachVisualizer(player.audioSessionId)
        updateProgress()
    }

    /** Переключает состояние play/pause без смены текущего трека. */
    fun togglePlayback() {
        if (player.isPlaying) {
            player.pause()
        } else if (player.mediaItemCount > 0) {
            ensurePlaybackServiceStarted()
            player.play()
        }
        updateProgress()
    }

    /** Перемещает позицию воспроизведения на указанное количество миллисекунд. */
    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
        updateProgress()
    }

    /** Включает или выключает режим повторения одного трека. */
    fun setRepeatOneEnabled(enabled: Boolean) {
        player.repeatMode = if (enabled) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_OFF
        }
    }

    /** Управляет захватом данных visualizer с учётом разрешения и видимости экрана. */
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

    /** Сохраняет новые настройки EQ и пытается сразу применить их к текущей аудиосессии. */
    fun setEqualizerSettings(settings: List<EqSetting>) {
        pendingEqSettings = settings
        applyEqualizerSettings()
    }

    /** Подключает callbacks навигации, чтобы уведомление и другие источники управляли очередью. */
    fun setNavigationCallbacks(
        onPreviousRequested: () -> Unit,
        onNextRequested: () -> Unit,
    ) {
        this.onPreviousRequested = onPreviousRequested
        this.onNextRequested = onNextRequested
    }

    /** Подключает callback, чтобы UI узнал о смене трека из уведомления или гарнитуры. */
    fun setTrackChangedCallback(onTrackChanged: (String) -> Unit) {
        this.onTrackChanged = onTrackChanged
    }

    /** Запрашивает переход к предыдущему треку через внешнюю логику очереди приложения. */
    fun requestPreviousTrack() {
        onPreviousRequested?.invoke()
    }

    /** Запрашивает переход к следующему треку через внешнюю логику очереди приложения. */
    fun requestNextTrack() {
        onNextRequested?.invoke()
    }

    /** Освобождает все ресурсы плеера и завершает внутренние coroutine-задачи. */
    fun release() {
        player.removeListener(listener)
        releaseEqualizer()
        releaseVisualizer()
        player.release()
        scope.cancel()
    }

    /** Обновляет публичные значения позиции, длительности и флага воспроизведения. */
    private fun updateProgress() {
        _positionMs.value = player.currentPosition.coerceAtLeast(0L)
        _durationMs.value = player.duration.takeIf { it > 0L } ?: 0L
        _isPlaying.value = player.isPlaying
    }

    /** Подключает системный Equalizer к текущей аудиосессии ExoPlayer. */
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

    /** Подключает Visualizer к текущей аудиосессии, если экрану нужны аудиоданные. */
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

    /** Преобразует пользовательские полосы EQ в реальные полосы устройства и применяет их. */
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

    /** Преобразует FFT-данные в более сглаженные значения для UI-анимаций. */
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

    /** Постепенно затухает visualizer, когда звук временно не воспроизводится. */
    private fun decayVisualizerData() {
        val decayedBands = _visualizerBands.value.map { value ->
            (value * 0.8f).takeIf { it > 0.012f } ?: 0f
        }
        _visualizerBands.value = decayedBands
        _visualizerLevel.value = (_visualizerLevel.value * 0.76f).takeIf { it > 0.02f } ?: 0f
    }

    /** Безопасно освобождает системный Equalizer. */
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

    /** Безопасно освобождает системный Visualizer. */
    private fun releaseVisualizer() {
        try {
            visualizer?.release()
        } catch (_: Throwable) {
            // Ignore release failures for devices with flaky Visualizer implementation.
        }
        visualizer = null
        visualizerAudioSessionId = null
    }

    /** Сбрасывает уровни visualizer, когда нет активной аудиосессии. */
    private fun resetVisualizerData() {
        _visualizerLevel.value = 0f
        _visualizerBands.value = List(VISUALIZER_SEGMENT_COUNT) { 0f }
    }

    /** Поднимает foreground-service, чтобы Android показал media-уведомление. */
    private fun ensurePlaybackServiceStarted() {
        val intent = Intent(appContext, PlaybackService::class.java)
        ContextCompat.startForegroundService(appContext, intent)
    }

    /** Проверяет, совпадает ли новая очередь с уже загруженной в ExoPlayer. */
    private fun isSameQueue(queue: List<Track>): Boolean {
        if (player.mediaItemCount != queue.size) return false
        return queue.indices.all { index ->
            player.getMediaItemAt(index).mediaId == queue[index].id
        }
    }

    /** Читает локально сохранённую обложку как байты, чтобы MediaSession мог отдать её SystemUI. */
    private fun loadArtworkBytes(artworkUri: String?): ByteArray? {
        val sourceUri = artworkUri?.let(Uri::parse) ?: return null
        return runCatching {
            when (sourceUri.scheme) {
                "file" -> File(checkNotNull(sourceUri.path)).takeIf { it.exists() }?.readBytes()
                else -> appContext.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
            }
        }.getOrNull()
    }

    private companion object {
        const val VISUALIZER_SEGMENT_COUNT = 24
    }
}

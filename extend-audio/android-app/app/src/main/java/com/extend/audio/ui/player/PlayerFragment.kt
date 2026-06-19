package com.extend.audio.ui.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.extend.audio.R
import com.extend.audio.databinding.FragmentPlayerBinding
import com.extend.audio.domain.model.Track
import com.extend.audio.ui.common.formatDuration
import com.extend.audio.ui.common.loadTrackArtwork
import com.extend.audio.ui.main.MainViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    private var isUserSeeking = false
    private var hasVisualizerPermission = false
    private var hasRequestedVisualizerPermission = false
    private var lastBoundArtworkKey: String? = null
    private var lastArtworkRefreshTrackId: String? = null

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasVisualizerPermission = granted
            syncVisualizerCaptureState()
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        hasVisualizerPermission = hasRecordAudioPermission()

        binding.buttonBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.buttonPrevious.setOnClickListener {
            viewModel.playPrevious()
        }

        binding.buttonPlayPause.setOnClickListener {
            viewModel.togglePlayback()
        }

        binding.buttonNext.setOnClickListener {
            viewModel.playNext()
        }

        binding.buttonShuffle.setOnClickListener {
            viewModel.toggleShuffle()
        }

        binding.buttonRepeat.setOnClickListener {
            viewModel.toggleRepeatOne()
        }

        binding.seekProgress.setOnSeekBarChangeListener(object :
            android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: android.widget.SeekBar?,
                progress: Int,
                fromUser: Boolean,
            ) = Unit

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                val targetPosition = seekBar?.progress?.toLong() ?: 0L
                viewModel.seekTo(targetPosition)
                isUserSeeking = false
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState
                        .map { state ->
                            PlayerScreenState(
                                tracks = state.tracks,
                                currentTrack = state.currentTrack,
                                currentPresetName = state.activePreset?.name?.takeIf { it.isNotBlank() },
                                isPlaying = state.isPlaying,
                                playbackPositionMs = state.playbackPositionMs,
                                playbackDurationMs = state.playbackDurationMs,
                                isEqualizerAvailable = state.isEqualizerAvailable,
                                isShuffleEnabled = state.isShuffleEnabled,
                                isRepeatOneEnabled = state.isRepeatOneEnabled,
                            )
                        }
                        .distinctUntilChanged()
                        .collect { state ->
                            bindScreenState(state)
                        }
                }

                launch {
                    viewModel.uiState
                        .map { state ->
                            PlayerGlowState(
                                hasTrack = state.currentTrack != null,
                                isPlaying = state.isPlaying,
                                quantizedLevel = (state.visualizerLevel * 14f).toInt(),
                            )
                        }
                        .distinctUntilChanged()
                        .collect { glowState ->
                            val renderLevel = glowState.quantizedLevel / 14f
                            binding.viewPlayButtonGlow.render(
                                enabled = glowState.hasTrack,
                                playing = glowState.isPlaying,
                                level = renderLevel,
                            )
                            updateCoverGlow(
                                isEnabled = glowState.hasTrack,
                                isPlaying = glowState.isPlaying,
                                level = renderLevel,
                            )
                        }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        syncVisualizerCaptureState()
    }

    override fun onStop() {
        viewModel.setAudioVisualizationEnabled(false, hasVisualizerPermission)
        super.onStop()
    }

    override fun onDestroyView() {
        lastBoundArtworkKey = null
        lastArtworkRefreshTrackId = null
        _binding = null
        super.onDestroyView()
    }

    private fun syncVisualizerCaptureState() {
        val isFragmentStarted =
            viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        viewModel.setAudioVisualizationEnabled(isFragmentStarted, hasVisualizerPermission)
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun updateCoverGlow(isEnabled: Boolean, isPlaying: Boolean, level: Float) {
        binding.viewCoverGlow.render(
            enabled = isEnabled,
            playing = isPlaying,
            level = level,
        )
    }

    private fun bindScreenState(state: PlayerScreenState) {
        val currentTrack = state.currentTrack
        val currentPreset = state.currentPresetName ?: getString(R.string.no_saved_presets)
        val currentTrackIndex = state.tracks.indexOfFirst { it.id == currentTrack?.id }

        binding.textTrackTitle.text =
            currentTrack?.title ?: getString(R.string.no_track_selected)
        binding.textTrackArtist.text =
            currentTrack?.artist ?: getString(R.string.unknown_artist)
        binding.textPreset.text = getString(R.string.player_preset, currentPreset)
        binding.textEqualizerStatus.text = getString(
            if (state.isEqualizerAvailable) {
                R.string.equalizer_active
            } else {
                R.string.equalizer_unavailable
            }
        )
        binding.textCurrentTime.text = formatDuration(state.playbackPositionMs)
        binding.textDuration.text = formatDuration(state.playbackDurationMs)

        val seekMax = state.playbackDurationMs
            .coerceAtLeast(1L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        binding.seekProgress.max = seekMax
        if (!isUserSeeking) {
            binding.seekProgress.progress =
                state.playbackPositionMs.coerceAtMost(seekMax.toLong()).toInt()
        }

        val playPauseIcon =
            if (state.isPlaying) {
                R.drawable.ic_pause_minimal
            } else {
                R.drawable.ic_play_minimal
            }
        val playPauseLabel = if (state.isPlaying) R.string.pause else R.string.play

        binding.buttonPlayPause.setImageResource(playPauseIcon)
        binding.buttonPlayPause.contentDescription = getString(playPauseLabel)
        binding.buttonShuffle.isSelected = state.isShuffleEnabled
        binding.buttonRepeat.isSelected = state.isRepeatOneEnabled

        binding.buttonPlayPause.isEnabled = currentTrack != null
        binding.buttonPrevious.isEnabled =
            if (state.isShuffleEnabled) {
                currentTrack != null && state.tracks.size > 1
            } else {
                currentTrackIndex > 0
            }
        binding.buttonNext.isEnabled =
            if (state.isShuffleEnabled) {
                currentTrack != null && state.tracks.size > 1
            } else {
                currentTrackIndex != -1 && currentTrackIndex < state.tracks.lastIndex
            }
        binding.buttonShuffle.isEnabled = state.tracks.size > 1
        binding.buttonRepeat.isEnabled = currentTrack != null

        val artworkKey = currentTrack?.let { "${it.id}:${it.artworkUri}" }
        if (artworkKey != lastBoundArtworkKey) {
            loadTrackArtwork(
                imageView = binding.imageCover,
                placeholderView = binding.imageCoverFallback,
                track = currentTrack,
            )
            lastBoundArtworkKey = artworkKey
        }

        when {
            currentTrack == null -> {
                lastArtworkRefreshTrackId = null
            }

            currentTrack.artworkUri.isNullOrBlank() &&
                lastArtworkRefreshTrackId != currentTrack.id -> {
                lastArtworkRefreshTrackId = currentTrack.id
                viewModel.refreshCurrentTrackArtworkIfMissing()
            }

            currentTrack.artworkUri.isNullOrBlank().not() -> {
                lastArtworkRefreshTrackId = null
            }
        }

        if (currentTrack != null &&
            !hasVisualizerPermission &&
            !hasRequestedVisualizerPermission
        ) {
            hasRequestedVisualizerPermission = true
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private data class PlayerScreenState(
        val tracks: List<Track>,
        val currentTrack: Track?,
        val currentPresetName: String?,
        val isPlaying: Boolean,
        val playbackPositionMs: Long,
        val playbackDurationMs: Long,
        val isEqualizerAvailable: Boolean,
        val isShuffleEnabled: Boolean,
        val isRepeatOneEnabled: Boolean,
    )

    private data class PlayerGlowState(
        val hasTrack: Boolean,
        val isPlaying: Boolean,
        val quantizedLevel: Int,
    )
}

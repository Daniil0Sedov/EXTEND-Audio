package com.extend.audio.ui.library

/** Экран библиотеки: импорт папки, поиск по трекам и переход на экран плеера. */

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.extend.audio.R
import com.extend.audio.databinding.FragmentLibraryBinding
import com.extend.audio.domain.model.Track
import com.extend.audio.ui.main.MainViewModel
import kotlinx.coroutines.launch

/** Экран библиотеки с импортом папки, поиском и открытием выбранного трека. */
class LibraryFragment : Fragment() {

    companion object {
        const val ARG_OPEN_PICKER = "openPicker"
    }

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    private val trackAdapter = TrackListAdapter(::onTrackSelected)
    private var searchQuery: String = ""
    private var latestTracks: List<Track> = emptyList()
    private var currentTrackId: String? = null
    private var isPlaybackActive: Boolean = false
    private var missingArtworkRecoveryRequested: Boolean = false

    private val openFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult

            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some providers do not grant persistable permission. Playback can still work.
            } catch (_: IllegalArgumentException) {
                // Some providers reject manual persistence. Ignore for MVP.
            }

            viewLifecycleOwner.lifecycleScope.launch {
                val result = viewModel.importFolder(uri)
                val messageRes = when {
                    result.supportedFilesCount == 0 -> R.string.no_audio_found_in_folder
                    result.importedCount > 0 && result.updatedCount > 0 ->
                        R.string.folder_import_updated_summary
                    result.importedCount > 0 -> R.string.folder_import_summary
                    result.updatedCount > 0 -> R.string.folder_import_artwork_updated_only
                    else -> R.string.folder_import_duplicates_only
                }

                val message = when (messageRes) {
                    R.string.folder_import_updated_summary -> getString(
                        messageRes,
                        result.importedCount,
                        result.updatedCount,
                        result.supportedFilesCount
                    )
                    R.string.folder_import_summary -> getString(
                        messageRes,
                        result.importedCount,
                        result.supportedFilesCount
                    )
                    R.string.folder_import_artwork_updated_only -> getString(
                        messageRes,
                        result.updatedCount,
                    )
                    else -> getString(messageRes)
                }

                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }

    /** Создаёт корневой view библиотеки через ViewBinding. */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** Инициализирует список треков, поиск и обработку аргумента автозапуска импорта. */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerTracks.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = trackAdapter
        }

        binding.buttonImport.setOnClickListener {
            launchFolderPicker()
        }

        binding.editSearch.doAfterTextChanged { text ->
            searchQuery = text?.toString().orEmpty()
            renderTracks(latestTracks)
        }

        observeUi()

        if (savedInstanceState == null && arguments?.getBoolean(ARG_OPEN_PICKER, false) == true) {
            arguments = bundleOf(ARG_OPEN_PICKER to false)
            binding.root.post { launchFolderPicker() }
        }
    }

    /** Очищает binding при уничтожении view фрагмента. */
    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    /** Открывает системный выбор папки для импорта локальной музыки. */
    private fun launchFolderPicker() {
        openFolderLauncher.launch(null)
    }

    /** Подписывается на общее состояние приложения и обновляет список треков на экране. */
    private fun observeUi() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    latestTracks = state.tracks
                    currentTrackId = state.currentTrack?.id
                    isPlaybackActive = state.isPlaying
                    binding.textTrackCount.text =
                        getString(R.string.library_track_count, state.tracks.size)
                    renderTracks(state.tracks)
                    trackAdapter.updatePlaybackState(currentTrackId, isPlaybackActive)

                    if (!missingArtworkRecoveryRequested &&
                        state.tracks.isNotEmpty() &&
                        state.tracks.any { it.artworkUri.isNullOrBlank() }
                    ) {
                        missingArtworkRecoveryRequested = true
                        viewModel.refreshMissingArtworkForLibrary()
                    }
                }
            }
        }
    }

    /** Фильтрует библиотеку по поисковому запросу и переключает пустые состояния. */
    private fun renderTracks(allTracks: List<Track>) {
        val normalizedQuery = searchQuery.trim()
        val filteredTracks = if (normalizedQuery.isBlank()) {
            allTracks
        } else {
            allTracks.filter { track ->
                track.title.contains(normalizedQuery, ignoreCase = true) ||
                    (track.artist?.contains(normalizedQuery, ignoreCase = true) == true)
            }
        }

        val isSearchMode = normalizedQuery.isNotBlank()
        binding.layoutEmptyState.isVisible = filteredTracks.isEmpty()
        binding.recyclerTracks.isVisible = filteredTracks.isNotEmpty()
        binding.textEmptyTitle.text = getString(
            if (isSearchMode) R.string.library_empty_search_title else R.string.library_empty_title
        )
        binding.textEmptyBody.text = getString(
            if (isSearchMode) R.string.library_empty_search_body else R.string.library_empty_body
        )
        trackAdapter.submitList(filteredTracks) {
            trackAdapter.updatePlaybackState(currentTrackId, isPlaybackActive)
        }
    }

    /** Открывает выбранный трек и переводит пользователя на экран плеера. */
    private fun onTrackSelected(track: Track) {
        viewModel.openTrack(track)
        findNavController().navigate(R.id.playerFragment)
    }
}

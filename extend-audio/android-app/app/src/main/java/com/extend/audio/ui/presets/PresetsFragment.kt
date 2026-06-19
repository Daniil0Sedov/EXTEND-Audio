package com.extend.audio.ui.presets

/** Экран библиотеки пресетов, где пользователь управляет сохранёнными настройками звука. */

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.extend.audio.R
import com.extend.audio.databinding.FragmentPresetsBinding
import com.extend.audio.domain.model.Preset
import com.extend.audio.ui.main.DeletePresetResult
import com.extend.audio.ui.main.MainViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** Экран списка пресетов с действиями применить, редактировать и удалить. */
class PresetsFragment : Fragment() {

    private var _binding: FragmentPresetsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private val presetAdapter = PresetListAdapter(
        onApplyClicked = ::onPresetApplyClicked,
        onEditClicked = ::onPresetEditClicked,
        onDeleteClicked = ::onPresetDeleteClicked,
    )

    /** Создаёт view экрана пресетов через ViewBinding. */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPresetsBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** Инициализирует список пресетов и подписку на состояние текущего активного пресета. */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerPresets.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = presetAdapter
        }

        binding.buttonCreatePreset.setOnClickListener {
            createPreset()
        }

        binding.buttonCreateFirstPreset.setOnClickListener {
            createPreset()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val activePresetName =
                        state.activePreset?.name?.takeIf { it.isNotBlank() }
                            ?: getString(R.string.no_saved_presets)
                    binding.textCurrentPreset.text =
                        getString(R.string.player_preset, activePresetName)
                    binding.layoutEmptyState.isVisible = state.presets.isEmpty()
                    binding.recyclerPresets.isVisible = state.presets.isNotEmpty()
                    presetAdapter.submitData(state.presets, state.activePreset?.id)
                }
            }
        }
    }

    /** Освобождает binding, когда view фрагмента больше не используется. */
    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    /** Делает выбранный пресет активным для текущего воспроизведения. */
    private fun onPresetApplyClicked(preset: Preset) {
        viewModel.applyPreset(preset.id)
        Toast.makeText(requireContext(), R.string.preset_applied, Toast.LENGTH_SHORT).show()
    }

    /** Подготавливает выбранный пресет к редактированию и открывает экран эквалайзера. */
    private fun onPresetEditClicked(preset: Preset) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.preparePresetForEditing(preset.id)
            findNavController().navigate(R.id.mixerFragment)
        }
    }

    /** Создаёт новый черновик пресета и переводит пользователя на экран эквалайзера. */
    private fun createPreset() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.prepareNewPresetDraft()
            findNavController().navigate(R.id.mixerFragment)
        }
    }

    /** Показывает подтверждение и удаляет пресет из локальной библиотеки. */
    private fun onPresetDeleteClicked(preset: Preset) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_preset_title)
            .setMessage(R.string.delete_preset_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    when (viewModel.deletePreset(preset.id)) {
                        DeletePresetResult.DELETED -> {
                            Toast.makeText(
                                requireContext(),
                                R.string.preset_deleted,
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        DeletePresetResult.NOT_FOUND -> Unit
                    }
                }
            }
            .show()
    }
}

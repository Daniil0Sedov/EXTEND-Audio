package com.extend.audio.ui.mixer

/** Экран эквалайзера для создания, редактирования и применения пользовательского пресета. */

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.extend.audio.R
import com.extend.audio.databinding.FragmentMixerBinding
import com.extend.audio.databinding.ItemMixerBandBinding
import com.extend.audio.domain.model.EqDefaults
import com.extend.audio.domain.model.EqSetting
import com.extend.audio.domain.model.Preset
import com.extend.audio.ui.main.MainViewModel
import com.extend.audio.ui.main.SavePresetResult
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Экран редактирования пресета и ручной настройки полос эквалайзера. */
class MixerFragment : Fragment() {

    /** Вспомогательная пара для хранения binding одной колонки эквалайзера. */
    private data class BandColumn(
        val binding: ItemMixerBandBinding,
    )

    private var _binding: FragmentMixerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    private var bandColumns: List<BandColumn> = emptyList()
    private var isBindingSliders = false
    private var isBindingMeta = false

    /** Создаёт view экрана эквалайзера через ViewBinding. */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMixerBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** Настраивает поля пресета, ползунки и подписку на активный черновик. */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        buildBandColumns(layoutInflater)

        binding.editPresetName.doAfterTextChanged { text ->
            if (isBindingMeta) return@doAfterTextChanged
            binding.inputPresetNameLayout.error = null
            viewModel.updatePresetName(text?.toString().orEmpty())
        }

        binding.editHeadphoneModel.doAfterTextChanged { text ->
            if (isBindingMeta) return@doAfterTextChanged
            binding.inputHeadphoneModelLayout.error = null
            viewModel.updatePresetModel(text?.toString().orEmpty())
        }

        binding.buttonApplyNow.setOnClickListener {
            viewModel.applyCurrentMix()
            Toast.makeText(requireContext(), R.string.mixer_ready, Toast.LENGTH_SHORT).show()
        }

        binding.buttonSavePreset.setOnClickListener {
            savePreset()
        }

        viewModel.ensureEditablePreset()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .map { state -> state.activePreset ?: buildFallbackPreset() }
                    .distinctUntilChanged()
                    .collect { preset ->
                        bindPresetMeta(preset)
                        bindBands(preset)
                    }
                }
        }
    }

    /** Очищает ссылки на колонки и binding при уничтожении view. */
    override fun onDestroyView() {
        bandColumns = emptyList()
        _binding = null
        super.onDestroyView()
    }

    /** Динамически создаёт набор полос эквалайзера и их обработчики. */
    private fun buildBandColumns(inflater: LayoutInflater) {
        binding.layoutBandsContainer.removeAllViews()
        bandColumns = EqDefaults.defaultBandLabels.mapIndexed { index, _ ->
            val itemBinding = ItemMixerBandBinding.inflate(inflater, binding.layoutBandsContainer, false)
            itemBinding.seekBand.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser || isBindingSliders) return
                    viewModel.updateBand(index, progress - 12)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
            binding.layoutBandsContainer.addView(itemBinding.root)
            BandColumn(itemBinding)
        }
    }

    /** Заполняет метаданные пресета: название, модель и режим создания/редактирования. */
    private fun bindPresetMeta(preset: Preset) {
        isBindingMeta = true
        if (binding.editPresetName.text?.toString() != preset.name) {
            binding.editPresetName.setText(preset.name)
        }
        if (binding.editHeadphoneModel.text?.toString() != preset.headphoneModel) {
            binding.editHeadphoneModel.setText(preset.headphoneModel)
        }
        isBindingMeta = false

        binding.textPresetStatus.text =
            if (preset.id.isBlank()) {
                getString(R.string.new_preset_label)
            } else {
                getString(R.string.editing_preset_label)
            }
        binding.buttonSavePreset.text =
            if (preset.id.isBlank()) {
                getString(R.string.save_preset)
            } else {
                getString(R.string.update_preset)
            }
    }

    /** Подставляет значения усиления в полосы и обновляет подписи на ползунках. */
    private fun bindBands(preset: Preset) {
        val normalizedBands = EqDefaults.normalizeBands(preset.bands)

        isBindingSliders = true
        bandColumns.forEachIndexed { index, column ->
            val band = normalizedBands.getOrNull(index) ?: EqSetting(EqDefaults.defaultBandLabels[index], 0)
            column.binding.textBandLabel.text = band.bandLabel
            column.binding.textBandValue.text = getString(R.string.mixer_gain_value, band.gain)
            column.binding.seekBand.progress = band.gain + 12
            column.binding.seekBand.isEnabled = true
        }
        isBindingSliders = false
    }

    /** Сохраняет или обновляет пресет с учётом валидации обязательных полей. */
    private fun savePreset() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = viewModel.savePreset()
            when (result) {
                SavePresetResult.CREATED -> {
                    Toast.makeText(requireContext(), R.string.mixer_saved, Toast.LENGTH_SHORT).show()
                    findNavController().navigate(
                        R.id.presetsFragment,
                        null,
                        navOptions { launchSingleTop = true }
                    )
                }

                SavePresetResult.UPDATED -> {
                    Toast.makeText(requireContext(), R.string.mixer_updated, Toast.LENGTH_SHORT).show()
                    findNavController().navigate(
                        R.id.presetsFragment,
                        null,
                        navOptions { launchSingleTop = true }
                    )
                }

                SavePresetResult.MISSING_NAME -> {
                    binding.inputPresetNameLayout.error = getString(R.string.save_preset_error_name)
                }

                SavePresetResult.MISSING_MODEL -> {
                    binding.inputHeadphoneModelLayout.error =
                        getString(R.string.save_preset_error_model)
                }

                SavePresetResult.MISSING_NAME_AND_MODEL -> {
                    binding.inputPresetNameLayout.error = getString(R.string.save_preset_error_name)
                    binding.inputHeadphoneModelLayout.error =
                        getString(R.string.save_preset_error_model)
                    Toast.makeText(
                        requireContext(),
                        R.string.save_preset_error_both,
                        Toast.LENGTH_SHORT
                    ).show()
                }

                SavePresetResult.NO_DRAFT -> {
                    viewModel.createNewPresetDraft()
                }
            }
        }
    }

    /** Создаёт пустой локальный пресет, если активного черновика пока нет. */
    private fun buildFallbackPreset() = Preset(
        id = "",
        name = "",
        headphoneModel = "",
        bands = EqDefaults.defaultBands(),
    )
}

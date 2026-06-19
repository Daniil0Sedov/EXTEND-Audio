package com.extend.audio.ui.profile

/** Упрощённый профиль со статистикой локальной MVP-сессии пользователя. */

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.extend.audio.R
import com.extend.audio.databinding.FragmentProfileBinding
import com.extend.audio.ui.main.MainViewModel
import kotlinx.coroutines.launch

/** Упрощённый экран профиля с локальной статистикой по трекам и пресетам. */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    /** Создаёт view профиля через ViewBinding. */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** Подписывается на состояние приложения и показывает локальную статистику пользователя. */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.textTracksCount.text =
                        getString(R.string.tracks_count, state.tracks.size)
                    binding.textPresetsCount.text =
                        getString(R.string.presets_count, state.presets.size)
                    binding.textDeviceModel.text = getString(
                        R.string.device_model,
                        state.activePreset?.headphoneModel?.takeIf { it.isNotBlank() }
                            ?: getString(R.string.device_none)
                    )
                }
            }
        }
    }

    /** Очищает binding при уничтожении view фрагмента. */
    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

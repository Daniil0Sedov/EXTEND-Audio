package com.extend.audio.ui.welcome

/** Стартовый экран, который вводит пользователя в основной сценарий приложения. */

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.extend.audio.R
import com.extend.audio.databinding.FragmentWelcomeBinding
import com.extend.audio.ui.library.LibraryFragment

/** Приветственный экран, который ведёт пользователя либо к импорту музыки, либо сразу в библиотеку. */
class WelcomeFragment : Fragment() {

    private var _binding: FragmentWelcomeBinding? = null
    private val binding get() = _binding!!

    /** Создаёт view стартового экрана через ViewBinding. */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentWelcomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** Назначает кнопкам переход в основной сценарий приложения. */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainFlowNavOptions = navOptions {
            popUpTo(R.id.welcomeFragment) {
                inclusive = true
            }
        }

        binding.buttonScan.setOnClickListener {
            findNavController().navigate(
                R.id.libraryFragment,
                bundleOf(LibraryFragment.ARG_OPEN_PICKER to true),
                mainFlowNavOptions
            )
        }

        binding.buttonSkip.setOnClickListener {
            findNavController().navigate(R.id.libraryFragment, null, mainFlowNavOptions)
        }
    }

    /** Освобождает binding, когда view экрана уничтожается. */
    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

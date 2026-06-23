package com.extend.audio

/** Единственная activity приложения, которая хостит все fragment-экраны и навигацию. */

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.extend.audio.databinding.ActivityMainBinding
import com.extend.audio.navigation.AppDestinations

/** Хост-activity, внутри которой работает весь навигационный граф приложения. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** Подключает layout, navController и показывает нижнюю навигацию только на нужных экранах. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        requestNotificationPermissionIfNeeded()

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        setupBottomNavigation(navController)
        binding.bottomNavigation.isVisible = false

        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNavigation.isVisible =
                destination.id in AppDestinations.bottomNavDestinations
            updateBottomNavigationSelection(destination.id)
        }
    }

    /** Подключает кастомный хотбар к навигации и сохраняет single-top поведение вкладок. */
    private fun setupBottomNavigation(navController: androidx.navigation.NavController) {
        binding.navLibrary.setOnClickListener {
            navigateToBottomDestination(navController, R.id.libraryFragment)
        }
        binding.navPresets.setOnClickListener {
            navigateToBottomDestination(navController, R.id.presetsFragment)
        }
        binding.navMixer.setOnClickListener {
            navigateToBottomDestination(navController, R.id.mixerFragment)
        }
        binding.navProfile.setOnClickListener {
            navigateToBottomDestination(navController, R.id.profileFragment)
        }
    }

    /** Переходит на нужную вкладку без дублирования одинаковых экранов в back stack. */
    private fun navigateToBottomDestination(
        navController: androidx.navigation.NavController,
        destinationId: Int,
    ) {
        if (navController.currentDestination?.id == destinationId) return

        navController.navigate(
            destinationId,
            null,
            navOptions {
                launchSingleTop = true
                restoreState = true
                popUpTo(navController.graph.startDestinationId) {
                    saveState = true
                }
            }
        )
    }

    /** Обновляет выбранный таб и прокидывает selected-state во внутренние view элемента. */
    private fun updateBottomNavigationSelection(destinationId: Int) {
        updateNavItemState(
            itemRoot = binding.navLibrary,
            iconChip = binding.navLibraryIconChip,
            icon = binding.navLibraryIcon,
            label = binding.navLibraryLabel,
            selected = destinationId == R.id.libraryFragment,
        )
        updateNavItemState(
            itemRoot = binding.navPresets,
            iconChip = binding.navPresetsIconChip,
            icon = binding.navPresetsIcon,
            label = binding.navPresetsLabel,
            selected = destinationId == R.id.presetsFragment,
        )
        updateNavItemState(
            itemRoot = binding.navMixer,
            iconChip = binding.navMixerIconChip,
            icon = binding.navMixerIcon,
            label = binding.navMixerLabel,
            selected = destinationId == R.id.mixerFragment,
        )
        updateNavItemState(
            itemRoot = binding.navProfile,
            iconChip = binding.navProfileIconChip,
            icon = binding.navProfileIcon,
            label = binding.navProfileLabel,
            selected = destinationId == R.id.profileFragment,
        )
    }

    /** Ставит selected-state на корень, плашку, иконку и подпись конкретного таба. */
    private fun updateNavItemState(
        itemRoot: View,
        iconChip: View,
        icon: View,
        label: View,
        selected: Boolean,
    ) {
        itemRoot.isSelected = selected
        iconChip.isSelected = selected
        icon.isSelected = selected
        label.isSelected = selected
    }

    /** Просит разрешение POST_NOTIFICATIONS, чтобы на Android 13+ было видно мини-плеер. */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

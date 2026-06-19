package com.extend.audio

/** Единственная activity приложения, которая хостит все fragment-экраны и навигацию. */

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.extend.audio.databinding.ActivityMainBinding
import com.extend.audio.navigation.AppDestinations

/** Хост-activity, внутри которой работает весь навигационный граф приложения. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** Подключает layout, navController и показывает нижнюю навигацию только на нужных экранах. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)
        binding.bottomNavigation.isVisible = false

        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNavigation.isVisible =
                destination.id in AppDestinations.bottomNavDestinations
        }
    }
}

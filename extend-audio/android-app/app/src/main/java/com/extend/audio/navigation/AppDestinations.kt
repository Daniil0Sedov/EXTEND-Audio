package com.extend.audio.navigation

/** Централизованный список экранов для нижней навигации и скрытых fragment-маршрутов. */

import com.extend.audio.R

object AppDestinations {
    val bottomNavDestinations = setOf(
        R.id.libraryFragment,
        R.id.presetsFragment,
        R.id.mixerFragment,
        R.id.profileFragment,
    )
}

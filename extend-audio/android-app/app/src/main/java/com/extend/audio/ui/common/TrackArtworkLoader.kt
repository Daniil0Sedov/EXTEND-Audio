package com.extend.audio.ui.common

import android.net.Uri
import android.widget.ImageView
import androidx.core.view.isVisible
import coil.load
import com.extend.audio.R
import com.extend.audio.domain.model.Track

fun loadTrackArtwork(
    imageView: ImageView,
    placeholderView: ImageView,
    track: Track?,
) {
    val artworkUri = track?.artworkUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)

    if (artworkUri == null) {
        imageView.setImageDrawable(null)
        placeholderView.isVisible = true
        return
    }

    placeholderView.isVisible = false
    imageView.load(artworkUri) {
        crossfade(true)
        listener(
            onSuccess = { _, _ ->
                placeholderView.isVisible = false
            },
            onError = { _, _ ->
                imageView.setImageDrawable(null)
                placeholderView.isVisible = true
            },
        )
    }
}

fun loadTrackArtwork(
    imageView: ImageView,
    track: Track?,
) {
    imageView.load(track?.artworkUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)) {
        crossfade(true)
        error(R.drawable.extend_logo)
        fallback(R.drawable.extend_logo)
    }
}

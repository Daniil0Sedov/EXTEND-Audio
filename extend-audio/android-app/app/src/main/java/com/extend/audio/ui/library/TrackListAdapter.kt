package com.extend.audio.ui.library

/** Адаптер карточек треков с подсветкой текущего воспроизводимого элемента. */

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.extend.audio.R
import com.extend.audio.databinding.ItemTrackBinding
import com.extend.audio.domain.model.Track
import com.extend.audio.ui.common.formatDuration
import com.extend.audio.ui.common.loadTrackArtwork

/** Адаптер карточек треков, который также подсвечивает текущий играющий элемент. */
class TrackListAdapter(
    private val onTrackClicked: (Track) -> Unit,
) : ListAdapter<Track, TrackListAdapter.TrackViewHolder>(TrackDiffCallback) {

    private var activeTrackId: String? = null
    private var isPlaybackActive: Boolean = false

    /** Создаёт view holder карточки трека. */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return TrackViewHolder(ItemTrackBinding.inflate(inflater, parent, false))
    }

    /** Привязывает конкретный трек к карточке списка. */
    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /** Останавливает анимации карточки перед повторным использованием holder. */
    override fun onViewRecycled(holder: TrackViewHolder) {
        holder.recycle()
        super.onViewRecycled(holder)
    }

    /** Обновляет состояние текущего играющего трека и перерисовывает только нужные элементы. */
    fun updatePlaybackState(trackId: String?, isPlaying: Boolean) {
        val previousTrackId = activeTrackId
        val previousIsPlaying = isPlaybackActive

        activeTrackId = trackId
        isPlaybackActive = isPlaying

        if (previousTrackId == trackId && previousIsPlaying == isPlaying) return

        notifyTrackChanged(previousTrackId)
        notifyTrackChanged(trackId)
    }

    /** Ищет позицию трека в текущем списке и точечно обновляет её в RecyclerView. */
    private fun notifyTrackChanged(trackId: String?) {
        if (trackId == null) return
        val position = currentList.indexOfFirst { it.id == trackId }
        if (position != RecyclerView.NO_POSITION) {
            notifyItemChanged(position)
        }
    }

    /** Holder одной карточки трека в библиотеке. */
    inner class TrackViewHolder(
        private val binding: ItemTrackBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        private var glowAnimator: AnimatorSet? = null

        /** Заполняет карточку метаданными трека и визуальным состоянием воспроизведения. */
        fun bind(track: Track) {
            val context = binding.root.context
            val artist = track.artist ?: context.getString(R.string.unknown_artist)
            val isCurrentTrack = activeTrackId == track.id
            val isCurrentlyPlaying = isCurrentTrack && isPlaybackActive

            binding.textTrackTitle.text = track.title
            binding.textTrackMeta.text = "$artist • ${formatDuration(track.durationMs)}"
            loadTrackArtwork(
                imageView = binding.imageTrackArt,
                placeholderView = binding.imageTrackFallback,
                track = track,
            )
            binding.textAction.text = when {
                isCurrentlyPlaying -> context.getString(R.string.track_item_playing)
                isCurrentTrack -> context.getString(R.string.track_item_selected)
                else -> context.getString(R.string.track_item_action)
            }

            binding.layoutTrackCard.setBackgroundResource(
                if (isCurrentTrack) {
                    R.drawable.bg_track_card_active
                } else {
                    R.drawable.bg_card_subtle
                }
            )

            if (isCurrentlyPlaying) {
                binding.viewTrackGlow.visibility = View.VISIBLE
                startGlowAnimation()
            } else {
                stopGlowAnimation()
            }

            binding.root.setOnClickListener { onTrackClicked(track) }
        }

        /** Очищает временные анимации при переиспользовании карточки. */
        fun recycle() {
            stopGlowAnimation()
        }

        /** Запускает мягкое свечение вокруг трека, который сейчас воспроизводится. */
        private fun startGlowAnimation() {
            if (glowAnimator != null) return

            binding.viewTrackGlow.alpha = 0.5f
            binding.viewTrackGlow.scaleX = 1.015f
            binding.viewTrackGlow.scaleY = 1.015f

            val alphaAnimator = ObjectAnimator.ofFloat(binding.viewTrackGlow, View.ALPHA, 0.38f, 0.82f, 0.38f).apply {
                duration = 3600L
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.RESTART
                interpolator = AccelerateDecelerateInterpolator()
            }
            val scaleXAnimator = ObjectAnimator.ofFloat(binding.viewTrackGlow, View.SCALE_X, 1.015f, 1.06f, 1.015f).apply {
                duration = 3600L
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.RESTART
                interpolator = AccelerateDecelerateInterpolator()
            }
            val scaleYAnimator = ObjectAnimator.ofFloat(binding.viewTrackGlow, View.SCALE_Y, 1.015f, 1.05f, 1.015f).apply {
                duration = 3600L
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.RESTART
                interpolator = AccelerateDecelerateInterpolator()
            }

            glowAnimator = AnimatorSet().apply {
                playTogether(alphaAnimator, scaleXAnimator, scaleYAnimator)
                start()
            }
        }

        /** Полностью останавливает glow-анимацию карточки. */
        private fun stopGlowAnimation() {
            glowAnimator?.cancel()
            glowAnimator = null
            binding.viewTrackGlow.alpha = 0f
            binding.viewTrackGlow.scaleX = 1f
            binding.viewTrackGlow.scaleY = 1f
            binding.viewTrackGlow.visibility = View.INVISIBLE
        }
    }

    /** DiffUtil для точного сравнения элементов списка треков. */
    private object TrackDiffCallback : DiffUtil.ItemCallback<Track>() {
        /** Сравнивает идентичность элементов по id трека. */
        override fun areItemsTheSame(oldItem: Track, newItem: Track): Boolean {
            return oldItem.id == newItem.id
        }

        /** Проверяет, изменилось ли содержимое трека целиком. */
        override fun areContentsTheSame(oldItem: Track, newItem: Track): Boolean {
            return oldItem == newItem
        }
    }
}

package com.extend.audio.player

/** Менеджер системного media-уведомления для EXTEND Audio. */

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerNotificationManager
import com.extend.audio.MainActivity
import com.extend.audio.R

/** Настраивает внешний вид и поведение мини-плеера в шторке Android. */
class PlaybackNotificationManager(
    private val context: Context,
    mediaSession: MediaSession,
    notificationListener: PlayerNotificationManager.NotificationListener,
) {

    private val manager =
        PlayerNotificationManager.Builder(
            context,
            NOTIFICATION_ID,
            CHANNEL_ID,
        )
            .setChannelNameResourceId(R.string.notification_channel_name)
            .setChannelDescriptionResourceId(R.string.notification_channel_description)
            .setMediaDescriptionAdapter(DescriptionAdapter(context))
            .setNotificationListener(notificationListener)
            .build()
            .apply {
                setMediaSessionToken(mediaSession.platformToken)
                setUseNextAction(true)
                setUsePreviousAction(true)
                setUseNextActionInCompactView(true)
                setUsePreviousActionInCompactView(true)
                setUseFastForwardAction(false)
                setUseRewindAction(false)
                setUseStopAction(false)
            }

    /** Подключает текущий Player к уведомлению или снимает его при остановке сервиса. */
    fun setPlayer(player: Player?) {
        manager.setPlayer(player)
    }

    /** Адаптер подставляет в уведомление заголовок, подпись, интент и обложку. */
    private class DescriptionAdapter(
        private val context: Context,
    ) : PlayerNotificationManager.MediaDescriptionAdapter {

        override fun getCurrentContentTitle(player: Player): CharSequence {
            return player.mediaMetadata.title ?: context.getString(R.string.app_name)
        }

        override fun createCurrentContentIntent(player: Player): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        override fun getCurrentContentText(player: Player): CharSequence {
            return player.mediaMetadata.artist ?: context.getString(R.string.unknown_artist)
        }

        override fun getCurrentLargeIcon(
            player: Player,
            callback: PlayerNotificationManager.BitmapCallback,
        ): Bitmap? {
            val artworkUri = player.mediaMetadata.artworkUri
            return artworkUri?.let { context.decodeArtworkBitmap(it) }
                ?: BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        }
    }

    private companion object {
        const val CHANNEL_ID = "extend_audio_playback"
        const val NOTIFICATION_ID = 3105
    }
}

/** Декодирует сохранённую локально обложку трека в bitmap для системного уведомления. */
private fun Context.decodeArtworkBitmap(uri: Uri): Bitmap? {
    return runCatching {
        contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
    }.getOrNull()
}

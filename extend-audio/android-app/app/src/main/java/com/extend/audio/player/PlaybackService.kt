package com.extend.audio.player

/** Foreground-service, который удерживает media-уведомление во время воспроизведения. */

import android.app.Notification
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.media3.ui.PlayerNotificationManager
import com.extend.audio.ExtendAudioApplication

/** Отдельный сервис для системного мини-плеера в шторке Android. */
class PlaybackService : android.app.Service() {

    private lateinit var notificationManager: PlaybackNotificationManager

    override fun onCreate() {
        super.onCreate()

        val app = application as ExtendAudioApplication
        notificationManager = PlaybackNotificationManager(
            context = this,
            mediaSession = app.mediaSession,
            notificationListener = object : PlayerNotificationManager.NotificationListener {
                override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }

                override fun onNotificationPosted(
                    notificationId: Int,
                    notification: Notification,
                    ongoing: Boolean,
                ) {
                    if (ongoing) {
                        ServiceCompat.startForeground(
                            this@PlaybackService,
                            notificationId,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                        )
                    } else {
                        stopForeground(STOP_FOREGROUND_DETACH)
                    }
                }
            },
        )
        notificationManager.setPlayer(app.playerController.exoPlayer)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREVIOUS -> (application as ExtendAudioApplication)
                .playerController
                .requestPreviousTrack()

            ACTION_NEXT -> (application as ExtendAudioApplication)
                .playerController
                .requestNextTrack()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        notificationManager.setPlayer(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_PREVIOUS = "com.extend.audio.action.PREVIOUS"
        const val ACTION_NEXT = "com.extend.audio.action.NEXT"
    }
}

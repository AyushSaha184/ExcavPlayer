package com.excavplayer.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.excavplayer.MainActivity
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.player.core.PlayerManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var playerManager: PlayerManager

    @Inject
    lateinit var logger: AppLogger

    private var mediaSession: MediaSession? = null

    companion object {
        private const val TAG = "PlaybackService"
    }

    override fun onCreate() {
        super.onCreate()
        logger.i(TAG, "Creating PlaybackService")

        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, playerManager.exoPlayer)
            .setSessionActivity(pendingIntent)
            .setCallback(MediaSessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.playbackState == Player.STATE_ENDED) {
            logger.i(TAG, "App task removed and playback is not active -> stopping PlaybackService")
            stopSelf()
        } else {
            logger.i(TAG, "App task removed but playback is active -> continuing in background")
        }
    }

    override fun onDestroy() {
        logger.i(TAG, "Destroying PlaybackService")
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private inner class MediaSessionCallback : MediaSession.Callback {
        // Can override onConnect, onCustomCommand, etc., as needed
    }
}

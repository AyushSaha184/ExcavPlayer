package com.excavplayer.player.playback

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.domain.model.PlayerCommand
import com.excavplayer.domain.model.Video
import com.excavplayer.player.core.PlayerController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PipHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AppLogger
) {
    companion object {
        private const val TAG = "PipHelper"
        const val ACTION_MEDIA_CONTROL = "com.excavplayer.ACTION_PIP_CONTROL"
        const val EXTRA_CONTROL_TYPE = "control_type"
        const val CONTROL_PLAY_PAUSE = 1
        const val CONTROL_FORWARD = 2
        const val CONTROL_REWIND = 3
    }

    val isPipSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    fun enterPictureInPicture(activity: Activity, video: Video?, isPlaying: Boolean): Boolean {
        if (!isPipSupported) {
            logger.w(TAG, "Picture-in-picture is not supported on this device")
            return false
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val params = buildPipParams(activity, video, isPlaying)
                activity.enterPictureInPictureMode(params)
            } else {
                false
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error entering Picture-in-Picture mode", e)
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun buildPipParams(activity: Activity, video: Video?, isPlaying: Boolean): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()

        // Calculate aspect ratio clamped to Android's allowed range (0.418 to 2.39)
        val width = video?.width ?: 16
        val height = video?.height ?: 9
        if (width > 0 && height > 0) {
            val ratio = (width.toFloat() / height.toFloat()).coerceIn(0.42f, 2.38f)
            val num = (ratio * 1000).toInt()
            builder.setAspectRatio(Rational(num, 1000))
        } else {
            builder.setAspectRatio(Rational(16, 9))
        }

        // Add remote actions (Play/Pause, Rewind, Fast Forward)
        val actions = mutableListOf<RemoteAction>()

        val rewindIntent = PendingIntent.getBroadcast(
            activity,
            CONTROL_REWIND,
            Intent(ACTION_MEDIA_CONTROL).putExtra(EXTRA_CONTROL_TYPE, CONTROL_REWIND),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        actions.add(
            RemoteAction(
                Icon.createWithResource(activity, android.R.drawable.ic_media_rew),
                "Rewind",
                "Rewind 10s",
                rewindIntent
            )
        )

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying) "Pause" else "Play"
        val playPauseIntent = PendingIntent.getBroadcast(
            activity,
            CONTROL_PLAY_PAUSE,
            Intent(ACTION_MEDIA_CONTROL).putExtra(EXTRA_CONTROL_TYPE, CONTROL_PLAY_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        actions.add(
            RemoteAction(
                Icon.createWithResource(activity, playPauseIcon),
                playPauseTitle,
                playPauseTitle,
                playPauseIntent
            )
        )

        val forwardIntent = PendingIntent.getBroadcast(
            activity,
            CONTROL_FORWARD,
            Intent(ACTION_MEDIA_CONTROL).putExtra(EXTRA_CONTROL_TYPE, CONTROL_FORWARD),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        actions.add(
            RemoteAction(
                Icon.createWithResource(activity, android.R.drawable.ic_media_ff),
                "Forward",
                "Forward 10s",
                forwardIntent
            )
        )

        builder.setActions(actions)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(true)
            builder.setSeamlessResizeEnabled(true)
        }

        return builder.build()
    }
}

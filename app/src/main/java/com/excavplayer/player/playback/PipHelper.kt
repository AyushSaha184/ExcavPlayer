package com.excavplayer.player.playback

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.domain.model.Video
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
    }

    val isPipSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    fun enterPictureInPicture(activity: Activity, video: Video?, isPlaying: Boolean): Boolean {
        if (activity.isFinishing || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed)) {
            logger.w(TAG, "Activity is finishing or destroyed, skipping PiP")
            return false
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            logger.w(TAG, "Picture-in-picture is not supported on this Android version")
            return false
        }

        return try {
            val params = buildPipParams(activity, video, isPlaying)
            activity.enterPictureInPictureMode(params)
        } catch (e: Exception) {
            logger.e(TAG, "Error entering Picture-in-Picture mode", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    @Suppress("DEPRECATION")
                    activity.enterPictureInPictureMode()
                    true
                } else {
                    false
                }
            } catch (fallbackEx: Exception) {
                logger.e(TAG, "Fallback enterPictureInPictureMode failed", fallbackEx)
                false
            }
        }
    }

    fun updateAutoPipParams(activity: Activity, video: Video?, isPlaying: Boolean, autoEnter: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = buildPipParams(activity, video, isPlaying, autoEnter = autoEnter)
                activity.setPictureInPictureParams(params)
            } catch (e: Exception) {
                logger.w(TAG, "Failed to update PictureInPictureParams: ${e.message}")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun buildPipParams(
        activity: Activity,
        video: Video?,
        isPlaying: Boolean,
        autoEnter: Boolean = false
    ): PictureInPictureParams {
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
            builder.setSeamlessResizeEnabled(true)
        }

        return builder.build()
    }
}

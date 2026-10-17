package com.excavplayer.player.playback

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.util.Rational
import android.view.View
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
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    fun enterPictureInPicture(
        activity: Activity,
        video: Video?,
        isPlaying: Boolean,
        sourceView: View? = null
    ): Boolean {
        if (activity.isFinishing || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed)) {
            logger.w(TAG, "Activity is finishing or destroyed, skipping PiP")
            return false
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            logger.w(TAG, "Picture-in-picture is not supported on this Android version")
            return false
        }

        val sourceRectHint = if (sourceView != null && sourceView.isAttachedToWindow) {
            val rect = Rect()
            sourceView.getGlobalVisibleRect(rect)
            if (!rect.isEmpty) rect else null
        } else null

        return try {
            val params = buildPipParams(activity, video, isPlaying, sourceRectHint = sourceRectHint)
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

    fun updateAutoPipParams(
        activity: Activity,
        video: Video?,
        isPlaying: Boolean,
        autoEnter: Boolean,
        sourceRectHint: Rect? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = buildPipParams(activity, video, isPlaying, autoEnter = autoEnter, sourceRectHint = sourceRectHint)
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
        autoEnter: Boolean = false,
        sourceRectHint: Rect? = null
    ): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()

        // Calculate aspect ratio clamped to Android's allowed range (0.4184 to 2.3900)
        val width = video?.width ?: 16
        val height = video?.height ?: 9
        if (width > 0 && height > 0) {
            val ratio = width.toFloat() / height.toFloat()
            val clampedRatio = ratio.coerceIn(0.42f, 2.38f)
            if (ratio == clampedRatio) {
                builder.setAspectRatio(Rational(width, height))
            } else {
                val num = (clampedRatio * 1000).toInt()
                builder.setAspectRatio(Rational(num, 1000))
            }
        } else {
            builder.setAspectRatio(Rational(16, 9))
        }

        if (sourceRectHint != null && !sourceRectHint.isEmpty) {
            builder.setSourceRectHint(sourceRectHint)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
            builder.setSeamlessResizeEnabled(true)
        }

        return builder.build()
    }
}

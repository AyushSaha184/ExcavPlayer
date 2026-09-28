package com.excavplayer.media.source

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.domain.model.MediaAvailability
import com.excavplayer.domain.model.MediaSourceType
import com.excavplayer.domain.model.Video
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaSourceResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AppLogger
) {
    companion object {
        private const val TAG = "MediaSourceResolver"
    }

    fun resolveSourceType(uri: Uri): MediaSourceType {
        return when {
            uri.scheme == ContentResolver.SCHEME_CONTENT && uri.authority?.contains("documents") == true -> {
                if (uri.toString().contains("tree")) MediaSourceType.LOCAL_TREE else MediaSourceType.LOCAL_DOCUMENT
            }
            uri.scheme == ContentResolver.SCHEME_CONTENT && (uri.authority == "media" || uri.authority?.contains("media") == true) -> {
                MediaSourceType.LOCAL_MEDIASTORE
            }
            uri.scheme == "http" || uri.scheme == "https" || uri.scheme == "smb" || uri.scheme == "webdav" -> {
                MediaSourceType.NETWORK_FUTURE
            }
            else -> MediaSourceType.LOCAL_MEDIASTORE
        }
    }

    fun verifyAccess(video: Video): MediaAvailability {
        val uri = Uri.parse(video.uri)
        return try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                pfd.close()
                MediaAvailability.AVAILABLE
            } else {
                MediaAvailability.UNAVAILABLE
            }
        } catch (e: SecurityException) {
            logger.w(TAG, "SecurityException verifying URI ${video.uri}: permission revoked")
            MediaAvailability.PERMISSION_REVOKED
        } catch (e: Exception) {
            logger.w(TAG, "Could not open URI ${video.uri}: ${e.message}")
            MediaAvailability.UNAVAILABLE
        }
    }
}

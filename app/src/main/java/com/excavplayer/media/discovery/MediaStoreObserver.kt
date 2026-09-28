package com.excavplayer.media.discovery

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.data.media.MediaStoreDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AppLogger
) {
    companion object {
        private const val TAG = "MediaStoreObserver"
    }

    fun observeChanges(): Flow<Uri?> = callbackFlow {
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                logger.d(TAG, "MediaStore onChange detected for URI: $uri")
                trySend(uri)
            }
        }

        context.contentResolver.registerContentObserver(
            MediaStoreDataSource.VIDEO_COLLECTION_URI,
            true,
            observer
        )

        awaitClose {
            logger.d(TAG, "Unregistering MediaStore content observer")
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
}

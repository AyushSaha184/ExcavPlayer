package com.excavplayer.domain.model

/**
 * Domain model representing a playable video item.
 * Independent of Android MediaStore cursors, Room entities, or Media3 MediaItems.
 */
data class Video(
    val id: String,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedSeconds: Long,
    val dateModifiedSeconds: Long,
    val width: Int,
    val height: Int,
    val bitrate: Long? = null,
    val frameRate: Float? = null,
    val orientation: Int = 0,
    val folderName: String = "",
    val folderPath: String = "",
    val relativePath: String = "",
    val sourceType: MediaSourceType = MediaSourceType.LOCAL_MEDIASTORE,
    val availability: MediaAvailability = MediaAvailability.AVAILABLE,
    val isFavorite: Boolean = false,
    val resumePositionMs: Long? = null
) {
    val aspectRatio: Float
        get() = if (height > 0 && width > 0) width.toFloat() / height.toFloat() else 16f / 9f

    val isPortrait: Boolean
        get() = orientation == 90 || orientation == 270 || (width > 0 && height > width)

    val formattedDuration: String
        get() {
            if (durationMs <= 0) return "00:00"
            val totalSeconds = durationMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

    val fileFormat: String
        get() {
            val ext = displayName.substringAfterLast('.', "").uppercase()
            if (ext.isNotEmpty() && ext.length in 2..5) return ext
            val mimeSub = mimeType.substringAfterLast('/', "").uppercase()
            if (mimeSub.isNotEmpty() && mimeSub != "OCTET-STREAM" && mimeSub != "*") return mimeSub
            return "VIDEO"
        }
}

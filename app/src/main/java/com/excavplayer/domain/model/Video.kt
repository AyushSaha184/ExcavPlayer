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

object NaturalOrderComparator : Comparator<String> {
    override fun compare(str1: String?, str2: String?): Int {
        val s1 = str1.orEmpty()
        val s2 = str2.orEmpty()
        var i1 = 0
        var i2 = 0
        while (i1 < s1.length && i2 < s2.length) {
            val c1 = s1[i1]
            val c2 = s2[i2]
            if (c1.isDigit() && c2.isDigit()) {
                var j1 = i1
                while (j1 < s1.length && s1[j1].isDigit()) j1++
                var j2 = i2
                while (j2 < s2.length && s2[j2].isDigit()) j2++

                val numStr1 = s1.substring(i1, j1)
                val numStr2 = s2.substring(i2, j2)

                val trimmed1 = numStr1.trimStart('0')
                val trimmed2 = numStr2.trimStart('0')

                val cmp = if (trimmed1.length != trimmed2.length) {
                    trimmed1.length.compareTo(trimmed2.length)
                } else {
                    trimmed1.compareTo(trimmed2)
                }

                if (cmp != 0) return cmp

                val lenCmp = numStr1.length.compareTo(numStr2.length)
                if (lenCmp != 0) return lenCmp

                i1 = j1
                i2 = j2
            } else {
                val cmp = c1.lowercaseChar().compareTo(c2.lowercaseChar())
                if (cmp != 0) return cmp
                i1++
                i2++
            }
        }
        return s1.length.compareTo(s2.length)
    }
}

val NaturalVideoComparator: Comparator<Video> = Comparator { v1, v2 ->
    NaturalOrderComparator.compare(v1.displayName, v2.displayName)
}


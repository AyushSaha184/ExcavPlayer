package com.excavplayer.data.database.mapper

import com.excavplayer.data.database.dao.FolderTuple
import com.excavplayer.data.database.dao.PlaylistItemWithVideoTuple
import com.excavplayer.data.database.dao.PlaylistWithCountTuple
import com.excavplayer.data.database.dao.VideoWithMetadataTuple
import com.excavplayer.data.database.entity.MediaSourceEntity
import com.excavplayer.data.database.entity.PlaybackEntity
import com.excavplayer.data.database.entity.PlaylistEntity
import com.excavplayer.data.database.entity.PlaylistItemEntity
import com.excavplayer.data.database.entity.VideoEntity
import com.excavplayer.data.database.entity.WatchHistoryEntity
import com.excavplayer.domain.model.Folder
import com.excavplayer.domain.model.MediaAvailability
import com.excavplayer.domain.model.MediaSource
import com.excavplayer.domain.model.MediaSourceType
import com.excavplayer.domain.model.PlaybackState
import com.excavplayer.domain.model.PlaybackStatus
import com.excavplayer.domain.model.Playlist
import com.excavplayer.domain.model.PlaylistItem
import com.excavplayer.domain.model.RepeatMode
import com.excavplayer.domain.model.Video
import com.excavplayer.domain.model.WatchHistoryEntry

fun VideoWithMetadataTuple.toDomain(): Video {
    return Video(
        id = video.id,
        uri = video.uri,
        displayName = video.displayName,
        mimeType = video.mimeType,
        durationMs = video.durationMs,
        sizeBytes = video.sizeBytes,
        dateAddedSeconds = video.dateAddedSeconds,
        dateModifiedSeconds = video.dateModifiedSeconds,
        width = video.width,
        height = video.height,
        bitrate = video.bitrate,
        frameRate = video.frameRate,
        orientation = video.orientation,
        folderName = video.folderName,
        folderPath = video.folderPath,
        relativePath = video.relativePath,
        sourceType = runCatching { MediaSourceType.valueOf(video.sourceType) }.getOrDefault(MediaSourceType.LOCAL_MEDIASTORE),
        availability = runCatching { MediaAvailability.valueOf(video.availability) }.getOrDefault(MediaAvailability.AVAILABLE),
        isFavorite = isFavorite,
        resumePositionMs = resumePositionMs
    )
}

fun VideoEntity.toDomain(isFavorite: Boolean = false, resumePositionMs: Long? = null): Video {
    return Video(
        id = id,
        uri = uri,
        displayName = displayName,
        mimeType = mimeType,
        durationMs = durationMs,
        sizeBytes = sizeBytes,
        dateAddedSeconds = dateAddedSeconds,
        dateModifiedSeconds = dateModifiedSeconds,
        width = width,
        height = height,
        bitrate = bitrate,
        frameRate = frameRate,
        orientation = orientation,
        folderName = folderName,
        folderPath = folderPath,
        relativePath = relativePath,
        sourceType = runCatching { MediaSourceType.valueOf(sourceType) }.getOrDefault(MediaSourceType.LOCAL_MEDIASTORE),
        availability = runCatching { MediaAvailability.valueOf(availability) }.getOrDefault(MediaAvailability.AVAILABLE),
        isFavorite = isFavorite,
        resumePositionMs = resumePositionMs
    )
}

fun Video.toEntity(): VideoEntity {
    return VideoEntity(
        id = id,
        uri = uri,
        displayName = displayName,
        mimeType = mimeType,
        durationMs = durationMs,
        sizeBytes = sizeBytes,
        dateAddedSeconds = dateAddedSeconds,
        dateModifiedSeconds = dateModifiedSeconds,
        width = width,
        height = height,
        bitrate = bitrate,
        frameRate = frameRate,
        orientation = orientation,
        folderName = folderName,
        folderPath = folderPath,
        relativePath = relativePath,
        sourceType = sourceType.name,
        availability = availability.name,
        lastScannedTimestamp = System.currentTimeMillis()
    )
}

fun PlaybackEntity.toDomain(): PlaybackState {
    return PlaybackState(
        videoId = videoId,
        currentPositionMs = currentPositionMs,
        durationMs = durationMs,
        bufferedPositionMs = bufferedPositionMs,
        playbackStatus = runCatching { PlaybackStatus.valueOf(playbackStatus) }.getOrDefault(PlaybackStatus.IDLE),
        isPlaying = false,
        playbackSpeed = playbackSpeed,
        volume = volume,
        repeatMode = runCatching { RepeatMode.valueOf(repeatMode) }.getOrDefault(RepeatMode.OFF),
        isShuffleEnabled = isShuffleEnabled,
        selectedAudioTrackId = selectedAudioTrackId,
        selectedSubtitleTrackId = selectedSubtitleTrackId,
        selectedVideoTrackId = selectedVideoTrackId,
        lastUpdatedTimestamp = lastUpdatedTimestamp
    )
}

fun PlaybackState.toEntity(): PlaybackEntity {
    return PlaybackEntity(
        videoId = videoId ?: "",
        currentPositionMs = currentPositionMs,
        durationMs = durationMs,
        bufferedPositionMs = bufferedPositionMs,
        playbackStatus = playbackStatus.name,
        playbackSpeed = playbackSpeed,
        volume = volume,
        repeatMode = repeatMode.name,
        isShuffleEnabled = isShuffleEnabled,
        selectedAudioTrackId = selectedAudioTrackId,
        selectedSubtitleTrackId = selectedSubtitleTrackId,
        selectedVideoTrackId = selectedVideoTrackId,
        lastUpdatedTimestamp = lastUpdatedTimestamp
    )
}

fun WatchHistoryEntity.toDomain(): WatchHistoryEntry {
    return WatchHistoryEntry(
        videoId = videoId,
        firstPlayedTimestamp = firstPlayedTimestamp,
        lastPlayedTimestamp = lastPlayedTimestamp,
        totalWatchDurationMs = totalWatchDurationMs,
        completionPercentage = completionPercentage,
        isCompleted = isCompleted,
        lastPositionMs = lastPositionMs
    )
}

fun WatchHistoryEntry.toEntity(): WatchHistoryEntity {
    return WatchHistoryEntity(
        videoId = videoId,
        firstPlayedTimestamp = firstPlayedTimestamp,
        lastPlayedTimestamp = lastPlayedTimestamp,
        totalWatchDurationMs = totalWatchDurationMs,
        completionPercentage = completionPercentage,
        isCompleted = isCompleted,
        lastPositionMs = lastPositionMs
    )
}

fun PlaylistWithCountTuple.toDomain(): Playlist {
    return Playlist(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        itemCount = itemCount
    )
}

fun PlaylistEntity.toDomain(itemCount: Int = 0): Playlist {
    return Playlist(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        itemCount = itemCount
    )
}

fun PlaylistItemWithVideoTuple.toDomain(): PlaylistItem {
    return PlaylistItem(
        id = item.id,
        playlistId = item.playlistId,
        videoId = item.videoId,
        orderIndex = item.orderIndex,
        addedAt = item.addedAt,
        video = video?.toDomain()
    )
}

fun FolderTuple.toDomain(): Folder {
    return Folder(
        name = folderName,
        path = folderPath,
        videoCount = videoCount,
        totalSizeBytes = totalSizeBytes
    )
}

fun MediaSourceEntity.toDomain(): MediaSource {
    return MediaSource(
        id = id,
        uri = uri,
        type = runCatching { MediaSourceType.valueOf(type) }.getOrDefault(MediaSourceType.LOCAL_DOCUMENT),
        name = name,
        isAccessible = isAccessible,
        lastValidatedTimestamp = lastValidatedTimestamp
    )
}

fun MediaSource.toEntity(): MediaSourceEntity {
    return MediaSourceEntity(
        id = id,
        uri = uri,
        type = type.name,
        name = name,
        isAccessible = isAccessible,
        lastValidatedTimestamp = lastValidatedTimestamp
    )
}

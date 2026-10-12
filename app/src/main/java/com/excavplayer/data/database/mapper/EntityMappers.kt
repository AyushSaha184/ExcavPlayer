package com.excavplayer.data.database.mapper

import com.excavplayer.data.database.dao.FolderTuple
import com.excavplayer.data.database.dao.VideoWithMetadataTuple
import com.excavplayer.data.database.entity.MediaSourceEntity
import com.excavplayer.data.database.entity.PlaybackEntity
import com.excavplayer.data.database.entity.VideoEntity
import com.excavplayer.domain.model.Folder
import com.excavplayer.domain.model.MediaAvailability
import com.excavplayer.domain.model.MediaSource
import com.excavplayer.domain.model.MediaSourceType
import com.excavplayer.domain.model.PlaybackState
import com.excavplayer.domain.model.PlaybackStatus
import com.excavplayer.domain.model.RepeatMode
import com.excavplayer.domain.model.Video

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
        resumePositionMs = resumePositionMs
    )
}

fun VideoEntity.toDomain(resumePositionMs: Long? = null): Video {
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

package com.excavplayer.player.tracks

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import com.excavplayer.domain.model.AudioTrack
import com.excavplayer.domain.model.SubtitleTrack
import com.excavplayer.domain.model.VideoTrack

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class TrackManager {

    fun extractAudioTracks(tracks: Tracks): List<AudioTrack> {
        val audioTracks = mutableListOf<AudioTrack>()
        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                val mediaTrackGroup = group.mediaTrackGroup
                for (i in 0 until mediaTrackGroup.length) {
                    val format = mediaTrackGroup.getFormat(i)
                    val id = format.id ?: "audio_${mediaTrackGroup.id}_$i"
                    val label = buildAudioLabel(format, i)
                    val isSelected = group.isTrackSelected(i)
                    audioTracks.add(
                        AudioTrack(
                            id = id,
                            label = label,
                            language = format.language,
                            mimeType = format.sampleMimeType,
                            channelCount = format.channelCount,
                            sampleRate = if (format.sampleRate != Format.NO_VALUE) format.sampleRate else null,
                            bitrate = if (format.bitrate != Format.NO_VALUE) format.bitrate else null,
                            isSelected = isSelected
                        )
                    )
                }
            }
        }
        return audioTracks
    }

    fun extractVideoTracks(tracks: Tracks): List<VideoTrack> {
        val videoTracks = mutableListOf<VideoTrack>()
        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_VIDEO) {
                val mediaTrackGroup = group.mediaTrackGroup
                for (i in 0 until mediaTrackGroup.length) {
                    val format = mediaTrackGroup.getFormat(i)
                    val id = format.id ?: "video_${mediaTrackGroup.id}_$i"
                    val isSelected = group.isTrackSelected(i)
                    videoTracks.add(
                        VideoTrack(
                            id = id,
                            label = format.label ?: "${format.width}x${format.height}",
                            width = if (format.width != Format.NO_VALUE) format.width else 0,
                            height = if (format.height != Format.NO_VALUE) format.height else 0,
                            frameRate = if (format.frameRate != Format.NO_VALUE.toFloat()) format.frameRate else null,
                            bitrate = if (format.bitrate != Format.NO_VALUE) format.bitrate else null,
                            mimeType = format.sampleMimeType,
                            isSelected = isSelected
                        )
                    )
                }
            }
        }
        return videoTracks
    }

    fun extractSubtitleTracks(tracks: Tracks): List<SubtitleTrack> {
        val subtitleTracks = mutableListOf<SubtitleTrack>()
        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_TEXT) {
                val mediaTrackGroup = group.mediaTrackGroup
                for (i in 0 until mediaTrackGroup.length) {
                    val format = mediaTrackGroup.getFormat(i)
                    val id = format.id ?: "sub_${mediaTrackGroup.id}_$i"
                    val label = buildSubtitleLabel(format, i)
                    val isSelected = group.isTrackSelected(i)
                    subtitleTracks.add(
                        SubtitleTrack(
                            id = id,
                            label = label,
                            language = format.language,
                            mimeType = format.sampleMimeType,
                            isEmbedded = true,
                            uri = null,
                            isSelected = isSelected
                        )
                    )
                }
            }
        }
        return subtitleTracks
    }

    fun selectTrack(
        player: Player,
        trackType: @C.TrackType Int,
        targetTrackId: String?
    ) {
        val currentParameters = player.trackSelectionParameters
        val builder = currentParameters.buildUpon()

        if (targetTrackId == null) {
            // Disable this track type or clear overrides
            if (trackType == C.TRACK_TYPE_TEXT) {
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            } else {
                builder.clearOverridesOfType(trackType)
            }
            player.trackSelectionParameters = builder.build()
            return
        }

        builder.setTrackTypeDisabled(trackType, false)
        builder.clearOverridesOfType(trackType)

        val tracks = player.currentTracks
        for (group in tracks.groups) {
            if (group.type == trackType) {
                val mediaTrackGroup = group.mediaTrackGroup
                for (i in 0 until mediaTrackGroup.length) {
                    val format = mediaTrackGroup.getFormat(i)
                    val id = format.id ?: when (trackType) {
                        C.TRACK_TYPE_AUDIO -> "audio_${mediaTrackGroup.id}_$i"
                        C.TRACK_TYPE_VIDEO -> "video_${mediaTrackGroup.id}_$i"
                        C.TRACK_TYPE_TEXT -> "sub_${mediaTrackGroup.id}_$i"
                        else -> "track_${mediaTrackGroup.id}_$i"
                    }
                    if (id == targetTrackId) {
                        builder.setOverrideForType(TrackSelectionOverride(mediaTrackGroup, i))
                        player.trackSelectionParameters = builder.build()
                        return
                    }
                }
            }
        }

        player.trackSelectionParameters = builder.build()
    }

    private fun buildAudioLabel(format: Format, index: Int): String {
        return format.label
            ?: format.language?.uppercase()?.let { "Audio ($it)" }
            ?: "Audio Track ${index + 1}"
    }

    private fun buildSubtitleLabel(format: Format, index: Int): String {
        return format.label
            ?: format.language?.uppercase()?.let { "Subtitle ($it)" }
            ?: "Subtitle ${index + 1}"
    }
}

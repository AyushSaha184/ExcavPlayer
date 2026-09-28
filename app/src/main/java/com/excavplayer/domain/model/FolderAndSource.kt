package com.excavplayer.domain.model

data class Folder(
    val name: String,
    val path: String,
    val videoCount: Int,
    val totalSizeBytes: Long
)

data class MediaSource(
    val id: String,
    val uri: String,
    val type: MediaSourceType,
    val name: String,
    val isAccessible: Boolean = true,
    val lastValidatedTimestamp: Long = System.currentTimeMillis()
)

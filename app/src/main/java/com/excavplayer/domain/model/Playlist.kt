package com.excavplayer.domain.model

data class Playlist(
    val id: Long = 0L,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val itemCount: Int = 0
)

data class PlaylistItem(
    val id: Long = 0L,
    val playlistId: Long,
    val videoId: String,
    val orderIndex: Int,
    val addedAt: Long = System.currentTimeMillis(),
    val video: Video? = null
)

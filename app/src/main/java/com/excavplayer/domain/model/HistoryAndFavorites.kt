package com.excavplayer.domain.model

data class Favorite(
    val videoId: String,
    val addedTimestamp: Long = System.currentTimeMillis()
)


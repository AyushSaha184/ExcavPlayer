package com.excavplayer.domain.model

enum class MediaSourceType {
    LOCAL_MEDIASTORE,
    LOCAL_DOCUMENT,
    LOCAL_TREE,
    NETWORK_FUTURE
}

enum class MediaAvailability {
    AVAILABLE,
    UNAVAILABLE,
    PERMISSION_REVOKED
}

enum class RepeatMode {
    OFF,
    REPEAT_ONE,
    REPEAT_ALL
}

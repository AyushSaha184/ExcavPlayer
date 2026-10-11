package com.excavplayer.subtitles

data class SubtitleSearchResult(
    val id: String,
    val source: String,
    val title: String,
    val fileName: String,
    val language: String,
    val languageCode: String = "en",
    val downloadUrl: String,
    val format: String = "srt",
    val hearingImpaired: Boolean = false,
    val rating: String? = null,
    val downloads: Int? = null
)

enum class SubtitleSource(val displayName: String) {
    ALL("All Sources"),
    OPENSUBTITLES("OpenSubtitles"),
    SUBDL("SubDL"),
    YIFY("YIFY Subtitles"),
    SUBSOURCE("SubSource")
}

data class SubtitleLanguage(val name: String, val code: String) {
    companion object {
        val SUPPORTED = listOf(
            SubtitleLanguage("All", "all"),
            SubtitleLanguage("English", "en"),
            SubtitleLanguage("Spanish", "es"),
            SubtitleLanguage("French", "fr"),
            SubtitleLanguage("German", "de"),
            SubtitleLanguage("Hindi", "hi"),
            SubtitleLanguage("Arabic", "ar"),
            SubtitleLanguage("Portuguese", "pt"),
            SubtitleLanguage("Russian", "ru"),
            SubtitleLanguage("Japanese", "ja"),
            SubtitleLanguage("Korean", "ko"),
            SubtitleLanguage("Chinese", "zh"),
            SubtitleLanguage("Italian", "it"),
            SubtitleLanguage("Indonesian", "id"),
            SubtitleLanguage("Turkish", "tr"),
            SubtitleLanguage("Vietnamese", "vi")
        )
    }
}

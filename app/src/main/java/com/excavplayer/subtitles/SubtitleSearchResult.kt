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
    YIFY("YIFY Subtitles")
}

data class SubtitleLanguage(val name: String, val code: String) {
    companion object {
        /** Single source of truth: [LanguageRegistry]. Kept as a shim for call sites. */
        val SUPPORTED: List<SubtitleLanguage> get() = LanguageRegistry.supported
    }
}

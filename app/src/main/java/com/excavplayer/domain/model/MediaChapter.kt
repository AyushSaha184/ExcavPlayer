package com.excavplayer.domain.model

enum class ChapterType {
    REGULAR,
    INTRO,
    OUTRO,
    RECAP,
    PREVIEW
}

data class MediaChapter(
    val id: String,
    val title: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val type: ChapterType = determineChapterType(title)
) {
    companion object {
        fun determineChapterType(title: String): ChapterType {
            val lower = title.lowercase().trim()
            return when {
                lower.contains("intro") || lower.contains("opening") || lower.contains("prologue") ||
                        lower.matches(Regex(".*\\b(op|ncop|intro)\\b.*")) -> ChapterType.INTRO
                lower.contains("outro") || lower.contains("ending") || lower.contains("credits") ||
                        lower.matches(Regex(".*\\b(ed|nced|outro)\\b.*")) -> ChapterType.OUTRO
                lower.contains("recap") -> ChapterType.RECAP
                lower.contains("preview") -> ChapterType.PREVIEW
                else -> ChapterType.REGULAR
            }
        }
    }
}

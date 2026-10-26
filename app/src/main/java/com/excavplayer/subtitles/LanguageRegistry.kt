package com.excavplayer.subtitles

/**
 * The single table of languages ExcavPlayer knows: display names for the UI,
 * search-API codes for the subtitle providers, and the track-selection alias
 * vocabulary shared with the player's track policy.
 *
 * Every language lookup routes here; no other file may hold a language table.
 */
object LanguageRegistry {

    /** UI-facing language list, including the "All" sentinel. */
    val supported: List<SubtitleLanguage> = listOf(
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

    /** Display name → search-API code ("English" → "en", "All" → "all"). */
    fun nameToCode(displayName: String): String? =
        supported.firstOrNull { it.name.equals(displayName, ignoreCase = true) }?.code

    /** Search-API code → display name ("ja" → "Japanese"); null when unknown. */
    fun displayNameFor(code: String): String? =
        supported.firstOrNull { it.code.equals(code, ignoreCase = true) }?.name

    /** Collapses ISO-639-2/3 codes to the 2-letter search-API code ("eng" → "en"). */
    fun normalizeLangCode(threeLetter: String): String = when (threeLetter.lowercase()) {
        "eng" -> "en"
        "spa" -> "es"
        "fre", "fra" -> "fr"
        "ger", "deu" -> "de"
        "hin" -> "hi"
        "ara" -> "ar"
        "por" -> "pt"
        "rus" -> "ru"
        "jpn" -> "ja"
        "kor" -> "ko"
        "chi", "zho" -> "zh"
        "ita" -> "it"
        "ind" -> "id"
        "tur" -> "tr"
        "vie" -> "vi"
        else -> threeLetter.take(2).lowercase()
    }

    /** Best-effort display name for any code-ish input; falls back to uppercase. */
    fun displayNameForAny(input: String): String =
        displayNameFor(normalizeLangCode(input)) ?: input.uppercase()

    // ── Track-selection aliases (audio/subtitle track matching) ─────────

    private val TRACK_ALIASES = mapOf(
        "english" to listOf("en", "eng", "english"),
        "hindi" to listOf("hi", "hin", "hindi"),
        "japanese" to listOf("ja", "jpn", "japanese"),
        "spanish" to listOf("es", "spa", "spanish", "espanol", "español"),
        "french" to listOf("fr", "fra", "fre", "french", "francais", "français"),
        "german" to listOf("de", "deu", "ger", "german", "deutsch"),
        "chinese" to listOf("zh", "zho", "chi", "chinese", "mandarin", "cantonese"),
        "korean" to listOf("ko", "kor", "korean"),
        "russian" to listOf("ru", "rus", "russian"),
        "portuguese" to listOf("pt", "por", "portuguese"),
        "italian" to listOf("it", "ita", "italian"),
        "telugu" to listOf("te", "tel", "telugu"),
        "tamil" to listOf("ta", "tam", "tamil"),
        "kannada" to listOf("kn", "kan", "kannada"),
        "malayalam" to listOf("ml", "mal", "malayalam"),
        "bengali" to listOf("bn", "ben", "bengali"),
        "marathi" to listOf("mr", "mar", "marathi"),
        "gujarati" to listOf("gu", "guj", "gujarati"),
        "punjabi" to listOf("pa", "pan", "punjabi"),
        "arabic" to listOf("ar", "ara", "arabic"),
        "turkish" to listOf("tr", "tur", "turkish"),
        "vietnamese" to listOf("vi", "vie", "vietnamese"),
        "thai" to listOf("th", "tha", "thai"),
        "indonesian" to listOf("id", "ind", "indonesian")
    )

    fun trackLanguageCodes(preferred: String?): List<String> {
        if (preferred.isNullOrBlank() || preferred.equals("auto", ignoreCase = true) || preferred.equals("none", ignoreCase = true) || preferred.equals("auto (default)", ignoreCase = true)) {
            return emptyList()
        }
        val key = preferred.trim().lowercase()
        return TRACK_ALIASES[key] ?: listOf(key)
    }

    fun matchesTrackLanguage(language: String?, label: String?, preferred: String): Boolean {
        if (preferred.isBlank() || preferred.equals("none", ignoreCase = true) || preferred.equals("auto", ignoreCase = true) || preferred.equals("auto (default)", ignoreCase = true)) {
            return false
        }
        val pref = preferred.trim().lowercase()
        val synonyms = trackLanguageCodes(pref)
        if (synonyms.isEmpty()) return false

        val lang = language?.trim()?.lowercase()?.takeIf { it.isNotBlank() && it != "und" }
        val lab = label?.trim()?.lowercase()?.takeIf { it.isNotBlank() }

        // 1. Direct language code / tag match
        if (lang != null) {
            if (synonyms.any { s -> lang == s || lang.startsWith("$s-") || lang.startsWith("${s}_") }) {
                return true
            }
            // If the track explicitly defines another recognized language family, prevent accidental fallback
            val belongsToOther = TRACK_ALIASES.entries.any { (otherKey, otherSyns) ->
                otherKey != pref && otherSyns.any { s -> lang == s || lang.startsWith("$s-") || lang.startsWith("${s}_") }
            }
            if (belongsToOther) {
                return false
            }
        }

        // 2. Tokenized whole-word match on label (prevents false positives like "clean" matching "en" or "stereo" matching "es")
        if (lab != null) {
            val words = lab.split(Regex("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ]+")).filter { it.isNotBlank() }
            if (words.any { word -> synonyms.any { s -> word.equals(s, ignoreCase = true) } }) {
                return true
            }
        }

        return false
    }
}

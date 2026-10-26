package com.excavplayer.player.policy

import com.excavplayer.domain.model.AudioTrack
import com.excavplayer.domain.model.SubtitleTrack

/**
 * Pure track-selection decisions: which audio/subtitle track the player should
 * settle on when tracks are discovered, given saved per-video preferences and
 * global language preferences. No player internals — fully unit-testable.
 */
object TrackSelectionPolicy {

    private val LANGUAGE_MAP = mapOf(
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

    fun getLanguageCodes(preferred: String?): List<String> {
        if (preferred.isNullOrBlank() || preferred.equals("auto", ignoreCase = true) || preferred.equals("none", ignoreCase = true) || preferred.equals("auto (default)", ignoreCase = true)) {
            return emptyList()
        }
        val key = preferred.trim().lowercase()
        return LANGUAGE_MAP[key] ?: listOf(key)
    }

    fun matchesLanguage(language: String?, label: String?, preferred: String): Boolean {
        if (preferred.isBlank() || preferred.equals("none", ignoreCase = true) || preferred.equals("auto", ignoreCase = true) || preferred.equals("auto (default)", ignoreCase = true)) {
            return false
        }
        val pref = preferred.trim().lowercase()
        val synonyms = getLanguageCodes(pref)
        if (synonyms.isEmpty()) return false

        val lang = language?.trim()?.lowercase()?.takeIf { it.isNotBlank() && it != "und" }
        val lab = label?.trim()?.lowercase()?.takeIf { it.isNotBlank() }

        // 1. Direct language code / tag match
        if (lang != null) {
            if (synonyms.any { s -> lang == s || lang.startsWith("$s-") || lang.startsWith("${s}_") }) {
                return true
            }
            // If the track explicitly defines another recognized language family, prevent accidental fallback
            val belongsToOther = LANGUAGE_MAP.entries.any { (otherKey, otherSyns) ->
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

    fun parseSavedAudioTrack(savedPref: String?): Triple<String?, String?, String?> {
        if (savedPref.isNullOrBlank()) return Triple(null, null, null)
        val parts = savedPref.split("|")
        val id = parts.getOrNull(0)?.takeIf { it.isNotBlank() }
        val lang = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
        val label = parts.getOrNull(2)?.takeIf { it.isNotBlank() }
        return Triple(id, lang, label)
    }

    /** Why the audio selection is what it is — provenance matters downstream. */
    sealed interface AudioChoice {
        /** Matched the per-video saved preference; also counts as a user choice for this session. */
        data class Restored(val track: AudioTrack) : AudioChoice

        /** Matched the global preferred-language setting; a default, not a user choice. */
        data class Preferred(val track: AudioTrack) : AudioChoice

        data object Keep : AudioChoice
    }

    /**
     * Which audio track a fresh session should start on.
     *
     * Order of preference (mirrors the original onTracksChanged logic):
     * 1. Saved per-video preference: by track id, then language, then label.
     * 2. Global preferred audio language.
     * 3. Keep whatever the player already selected.
     */
    fun chooseAudioTrack(
        available: List<AudioTrack>,
        savedPreference: String?,
        preferredLanguage: String?
    ): AudioChoice {
        if (available.isEmpty()) return AudioChoice.Keep

        if (!savedPreference.isNullOrBlank()) {
            val (savedId, savedLang, savedLabel) = parseSavedAudioTrack(savedPreference)
            val matched = available.firstOrNull { it.id == savedId }
                ?: if (!savedLang.isNullOrBlank()) available.firstOrNull { matchesLanguage(it.language, it.label, savedLang) } else null
                ?: if (!savedLabel.isNullOrBlank()) available.firstOrNull { it.label.equals(savedLabel, ignoreCase = true) } else null
            if (matched != null) return AudioChoice.Restored(matched)
        }

        if (!preferredLanguage.isNullOrBlank()) {
            available.firstOrNull { matchesLanguage(it.language, it.label, preferredLanguage) }
                ?.let { return AudioChoice.Preferred(it) }
        }

        return AudioChoice.Keep
    }

    /**
     * The subtitle track id the selection should settle on after this update.
     * Returns null when no subtitle should be selected (including the case
     * where one is currently selected and must be deselected).
     */
    fun chooseSubtitleTrack(
        available: List<SubtitleTrack>,
        currentlySelectedId: String?,
        subtitlesEnabled: Boolean,
        userDisabledForSession: Boolean,
        preferredLanguage: String?
    ): String? {
        if (!subtitlesEnabled || userDisabledForSession) return null
        if (currentlySelectedId != null) return currentlySelectedId
        if (available.isEmpty()) return null

        return if (!preferredLanguage.isNullOrBlank()) {
            available.find { matchesLanguage(it.language, it.label, preferredLanguage) } ?: available.first()
        } else {
            available.first()
        }.id
    }
}

package com.excavplayer.player.policy

import com.excavplayer.domain.model.AudioTrack
import com.excavplayer.domain.model.SubtitleTrack
import com.excavplayer.subtitles.LanguageRegistry

/**
 * Pure track-selection decisions: which audio/subtitle track the player should
 * settle on when tracks are discovered, given saved per-video preferences and
 * global language preferences. No player internals — fully unit-testable.
 */
object TrackSelectionPolicy {

    fun getLanguageCodes(preferred: String?): List<String> =
        LanguageRegistry.trackLanguageCodes(preferred)

    fun matchesLanguage(language: String?, label: String?, preferred: String): Boolean =
        LanguageRegistry.matchesTrackLanguage(language, label, preferred)

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

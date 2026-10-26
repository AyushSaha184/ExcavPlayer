package com.excavplayer.policy

import com.excavplayer.domain.model.AudioTrack
import com.excavplayer.domain.model.SubtitleTrack
import com.excavplayer.player.policy.TrackSelectionPolicy
import com.excavplayer.player.policy.TrackSelectionPolicy.AudioChoice
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TrackSelectionPolicyTest {

    private fun audio(
        id: String,
        language: String? = null,
        label: String = id,
        isSelected: Boolean = false
    ) = AudioTrack(
        id = id,
        label = label,
        language = language,
        channelCount = 2,
        sampleRate = 48000,
        bitrate = 128000,
        isSelected = isSelected
    )

    private fun sub(
        id: String,
        language: String? = null,
        label: String = id,
        isSelected: Boolean = false
    ) = SubtitleTrack(
        id = id,
        label = label,
        language = language,
        isSelected = isSelected
    )

    // ── parseSavedAudioTrack (moved from PlayerManager) ──────────────

    @Test
    fun `parseSavedAudioTrack extracts id language and label correctly`() {
        val (id, lang, label) = TrackSelectionPolicy.parseSavedAudioTrack("audio_0_1|ja|Japanese")
        assertThat(id).isEqualTo("audio_0_1")
        assertThat(lang).isEqualTo("ja")
        assertThat(label).isEqualTo("Japanese")

        val (idOnly, langNull, labelNull) = TrackSelectionPolicy.parseSavedAudioTrack("audio_0_1")
        assertThat(idOnly).isEqualTo("audio_0_1")
        assertThat(langNull).isNull()
        assertThat(labelNull).isNull()

        val (nullId, nullLang, nullLabel) = TrackSelectionPolicy.parseSavedAudioTrack(null)
        assertThat(nullId).isNull()
        assertThat(nullLang).isNull()
        assertThat(nullLabel).isNull()

        val (emptyId, emptyLang, emptyLabel) = TrackSelectionPolicy.parseSavedAudioTrack("")
        assertThat(emptyId).isNull()
        assertThat(emptyLang).isNull()
        assertThat(emptyLabel).isNull()
    }

    // ── matchesLanguage (moved from PlayerManager) ───────────────────

    @Test
    fun `matchesLanguage correctly matches language codes and names`() {
        // English matches "en", "eng", "english"
        assertThat(TrackSelectionPolicy.matchesLanguage(language = "en", label = "Stereo", preferred = "English")).isTrue()
        assertThat(TrackSelectionPolicy.matchesLanguage(language = "eng", label = "Surround", preferred = "English")).isTrue()
        assertThat(TrackSelectionPolicy.matchesLanguage(language = null, label = "English Audio", preferred = "English")).isTrue()

        // Japanese matches "ja", "jpn", "japanese"
        assertThat(TrackSelectionPolicy.matchesLanguage(language = "ja", label = "Original", preferred = "Japanese")).isTrue()
        assertThat(TrackSelectionPolicy.matchesLanguage(language = "jpn", label = "Track 2", preferred = "Japanese")).isTrue()
        assertThat(TrackSelectionPolicy.matchesLanguage(language = "en", label = "English", preferred = "Japanese")).isFalse()

        // German
        assertThat(TrackSelectionPolicy.matchesLanguage(language = "de", label = "Deutsch", preferred = "German")).isTrue()
        assertThat(TrackSelectionPolicy.matchesLanguage(language = "fr", label = "French", preferred = "German")).isFalse()
    }

    // ── chooseAudioTrack ─────────────────────────────────────────────

    @Test
    fun `chooseAudioTrack restores by saved id first`() {
        val tracks = listOf(audio("a1", "en", "English"), audio("a2", "ja", "Japanese"))
        val choice = TrackSelectionPolicy.chooseAudioTrack(tracks, savedPreference = "a2|en|English", preferredLanguage = "English")
        assertThat(choice).isInstanceOf(AudioChoice.Restored::class.java)
        assertThat((choice as AudioChoice.Restored).track.id).isEqualTo("a2")
    }

    @Test
    fun `chooseAudioTrack falls back to saved language when id is unknown`() {
        val tracks = listOf(audio("a1", "en", "English"), audio("a2", "ja", "Japanese"))
        val choice = TrackSelectionPolicy.chooseAudioTrack(tracks, savedPreference = "gone|ja|Japanese", preferredLanguage = "English")
        assertThat((choice as AudioChoice.Restored).track.id).isEqualTo("a2")
    }

    @Test
    fun `chooseAudioTrack falls back to saved label when id and language are unknown`() {
        val tracks = listOf(audio("a1", "xx", "Director's Cut"), audio("a2", "ja", "Japanese"))
        val choice = TrackSelectionPolicy.chooseAudioTrack(tracks, savedPreference = "gone|xx|Director's Cut", preferredLanguage = "English")
        assertThat((choice as AudioChoice.Restored).track.id).isEqualTo("a1")
    }

    @Test
    fun `chooseAudioTrack falls through to preferred language when saved preference matches nothing`() {
        val tracks = listOf(audio("a1", "en", "English"), audio("a2", "ja", "Japanese"))
        val choice = TrackSelectionPolicy.chooseAudioTrack(tracks, savedPreference = "gone|zz|Missing", preferredLanguage = "Japanese")
        assertThat(choice).isInstanceOf(AudioChoice.Preferred::class.java)
        assertThat((choice as AudioChoice.Preferred).track.id).isEqualTo("a2")
    }

    @Test
    fun `chooseAudioTrack uses preferred language when no saved preference`() {
        val tracks = listOf(audio("a1", "en", "English"), audio("a2", "hi", "Hindi"))
        val choice = TrackSelectionPolicy.chooseAudioTrack(tracks, savedPreference = null, preferredLanguage = "Hindi")
        assertThat((choice as AudioChoice.Preferred).track.id).isEqualTo("a2")
    }

    @Test
    fun `chooseAudioTrack keeps current for auto, none, or empty tracks`() {
        val tracks = listOf(audio("a1", "en", "English"))
        assertThat(TrackSelectionPolicy.chooseAudioTrack(tracks, null, "auto")).isEqualTo(AudioChoice.Keep)
        assertThat(TrackSelectionPolicy.chooseAudioTrack(tracks, null, "none")).isEqualTo(AudioChoice.Keep)
        assertThat(TrackSelectionPolicy.chooseAudioTrack(tracks, null, null)).isEqualTo(AudioChoice.Keep)
        assertThat(TrackSelectionPolicy.chooseAudioTrack(emptyList(), "a1|en|English", "English")).isEqualTo(AudioChoice.Keep)
    }

    // ── chooseSubtitleTrack ──────────────────────────────────────────

    @Test
    fun `chooseSubtitleTrack picks preferred language when enabled`() {
        val subs = listOf(sub("s1", "en"), sub("s2", "ja"))
        val chosen = TrackSelectionPolicy.chooseSubtitleTrack(subs, null, subtitlesEnabled = true, userDisabledForSession = false, preferredLanguage = "Japanese")
        assertThat(chosen).isEqualTo("s2")
    }

    @Test
    fun `chooseSubtitleTrack falls back to first track when preferred language is missing`() {
        val subs = listOf(sub("s1", "en"), sub("s2", "ja"))
        val chosen = TrackSelectionPolicy.chooseSubtitleTrack(subs, null, subtitlesEnabled = true, userDisabledForSession = false, preferredLanguage = "de")
        assertThat(chosen).isEqualTo("s1")
    }

    @Test
    fun `chooseSubtitleTrack picks first when no preference set`() {
        val subs = listOf(sub("s1", "en"), sub("s2", "ja"))
        val chosen = TrackSelectionPolicy.chooseSubtitleTrack(subs, null, subtitlesEnabled = true, userDisabledForSession = false, preferredLanguage = null)
        assertThat(chosen).isEqualTo("s1")
    }

    @Test
    fun `chooseSubtitleTrack keeps the current selection when enabled`() {
        val subs = listOf(sub("s1", "en"), sub("s2", "ja", isSelected = true))
        val chosen = TrackSelectionPolicy.chooseSubtitleTrack(subs, "s2", subtitlesEnabled = true, userDisabledForSession = false, preferredLanguage = "de")
        assertThat(chosen).isEqualTo("s2")
    }

    @Test
    fun `chooseSubtitleTrack deselects when disabled or user-disabled`() {
        val subs = listOf(sub("s1", "en"))
        assertThat(TrackSelectionPolicy.chooseSubtitleTrack(subs, "s1", subtitlesEnabled = false, userDisabledForSession = false, preferredLanguage = "en")).isNull()
        assertThat(TrackSelectionPolicy.chooseSubtitleTrack(subs, "s1", subtitlesEnabled = true, userDisabledForSession = true, preferredLanguage = "en")).isNull()
    }

    @Test
    fun `chooseSubtitleTrack returns null when nothing is available`() {
        assertThat(TrackSelectionPolicy.chooseSubtitleTrack(emptyList(), null, subtitlesEnabled = true, userDisabledForSession = false, preferredLanguage = "en")).isNull()
    }
}

package com.excavplayer.player

import com.google.common.truth.Truth.assertThat
import com.excavplayer.domain.model.PlayerCommand
import com.excavplayer.domain.model.PlayerState
import com.excavplayer.player.core.PlayerManager
import org.junit.Test

class PlayerMuteAndAudioTrackTest {

    @Test
    fun `initial player state has isMuted false by default`() {
        val state = PlayerState()
        assertThat(state.isMuted).isFalse()
    }

    @Test
    fun `player command SetMuted correctly encapsulates muted boolean`() {
        val cmdMute = PlayerCommand.SetMuted(true)
        val cmdUnmute = PlayerCommand.SetMuted(false)

        assertThat(cmdMute.muted).isTrue()
        assertThat(cmdUnmute.muted).isFalse()
    }

    @Test
    fun `parseSavedAudioTrack extracts id language and label correctly`() {
        val (id, lang, label) = PlayerManager.parseSavedAudioTrack("audio_0_1|ja|Japanese")
        assertThat(id).isEqualTo("audio_0_1")
        assertThat(lang).isEqualTo("ja")
        assertThat(label).isEqualTo("Japanese")

        val (idOnly, langNull, labelNull) = PlayerManager.parseSavedAudioTrack("audio_0_1")
        assertThat(idOnly).isEqualTo("audio_0_1")
        assertThat(langNull).isNull()
        assertThat(labelNull).isNull()

        val (nullId, nullLang, nullLabel) = PlayerManager.parseSavedAudioTrack(null)
        assertThat(nullId).isNull()
        assertThat(nullLang).isNull()
        assertThat(nullLabel).isNull()

        val (emptyId, emptyLang, emptyLabel) = PlayerManager.parseSavedAudioTrack("")
        assertThat(emptyId).isNull()
        assertThat(emptyLang).isNull()
        assertThat(emptyLabel).isNull()
    }

    @Test
    fun `matchesLanguage correctly matches language codes and names`() {
        // English matches "en", "eng", "english"
        assertThat(PlayerManager.matchesLanguage(language = "en", label = "Stereo", preferred = "English")).isTrue()
        assertThat(PlayerManager.matchesLanguage(language = "eng", label = "Surround", preferred = "English")).isTrue()
        assertThat(PlayerManager.matchesLanguage(language = null, label = "English Audio", preferred = "English")).isTrue()

        // Japanese matches "ja", "jpn", "japanese"
        assertThat(PlayerManager.matchesLanguage(language = "ja", label = "Original", preferred = "Japanese")).isTrue()
        assertThat(PlayerManager.matchesLanguage(language = "jpn", label = "Track 2", preferred = "Japanese")).isTrue()
        assertThat(PlayerManager.matchesLanguage(language = "en", label = "English", preferred = "Japanese")).isFalse()

        // German
        assertThat(PlayerManager.matchesLanguage(language = "de", label = "Deutsch", preferred = "German")).isTrue()
        assertThat(PlayerManager.matchesLanguage(language = "fr", label = "French", preferred = "German")).isFalse()
    }
}

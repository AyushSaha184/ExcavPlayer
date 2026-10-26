package com.excavplayer.player

import com.google.common.truth.Truth.assertThat
import com.excavplayer.domain.model.PlayerState
import org.junit.Test

class PlayerMuteAndAudioTrackTest {

    @Test
    fun `initial player state has isMuted false by default`() {
        val state = PlayerState()
        assertThat(state.isMuted).isFalse()
    }
}

package com.excavplayer.player

import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import com.google.common.truth.Truth.assertThat
import com.excavplayer.domain.model.PlayerState
import org.junit.Test

class PlayerMuteAndAudioTrackTest {

    @Test
    fun `initial player state has isMuted false by default`() {
        val state = PlayerState()
        assertThat(state.isMuted).isFalse()
    }

    @Test
    fun `disabling audio track type in parameters reflects in disabledTrackTypes`() {
        val initialParams = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
        assertThat(initialParams.disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO)).isFalse()

        val mutedParams = initialParams.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .build()
        assertThat(mutedParams.disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO)).isTrue()

        val unmutedParams = mutedParams.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .build()
        assertThat(unmutedParams.disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO)).isFalse()
    }

    @Test
    fun `zero volume disables audio track type in parameters`() {
        val volume = 0f
        val shouldDisable = volume == 0f
        val params = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, shouldDisable)
            .build()
        assertThat(params.disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO)).isTrue()
    }
}

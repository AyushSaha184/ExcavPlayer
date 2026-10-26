package com.excavplayer.policy

import com.excavplayer.player.policy.AutoAdvancePolicy
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AutoAdvancePolicyTest {

    @Test
    fun `advances only when the setting is on and a next video exists`() {
        assertThat(AutoAdvancePolicy.shouldAdvance(autoplayNextVideo = true, hasNext = true)).isTrue()
        assertThat(AutoAdvancePolicy.shouldAdvance(autoplayNextVideo = true, hasNext = false)).isFalse()
        assertThat(AutoAdvancePolicy.shouldAdvance(autoplayNextVideo = false, hasNext = true)).isFalse()
        assertThat(AutoAdvancePolicy.shouldAdvance(autoplayNextVideo = false, hasNext = false)).isFalse()
    }
}

package com.jpillion.dailyreadingplanner.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * VD-T4 (D-V3-18): the in-app reader is a real, selectable provider — multi-ref capable, no
 * app required — and its promotion does NOT change the default or the unknown-id degradation
 * contract (in-app is never a silent default; that is D-V3-19, enforced elsewhere).
 */
class BibleProviderInAppTest {
    @Test
    fun `in-app renders the whole portion natively`() {
        assertThat(BibleProvider.IN_APP.multiRefCapable).isTrue()
    }

    @Test
    fun `in-app requires no installed app`() {
        assertThat(BibleProvider.IN_APP.requiresApp).isFalse()
    }

    @Test
    fun `the default provider is still BLB - in-app is never the silent default`() {
        assertThat(BibleProvider.DEFAULT).isEqualTo(BibleProvider.BLB)
    }

    @Test
    fun `in-app round-trips through its stored id`() {
        assertThat(BibleProvider.fromStored("IN_APP")).isEqualTo(BibleProvider.IN_APP)
    }

    @Test
    fun `an unknown stored id still degrades to BLB`() {
        assertThat(BibleProvider.fromStored("garbage")).isEqualTo(BibleProvider.BLB)
    }

    @Test
    fun `a null stored id still degrades to BLB`() {
        assertThat(BibleProvider.fromStored(null)).isEqualTo(BibleProvider.BLB)
    }
}

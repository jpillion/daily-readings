package com.jpillion.dailyreadingplanner.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Sprint K (D-23-1): the two destination axes that replaced the single BibleProvider enum.
 *  - [ReadingDestinationMode]: in-app vs. external; the DEFAULT is EXTERNAL so the in-app reader
 *    is never a silent default (D-V3-19 in spirit), and unknown stored ids degrade to it.
 *  - [ExternalBibleApp]: the four external apps; DEFAULT is BLB; the legacy "IN_APP" value (no
 *    longer a member) and any garbage degrade to BLB.
 */
class ReadingDestinationModelTest {
    @Test
    fun `destination mode default is external - the in-app reader is never the silent default`() {
        assertThat(ReadingDestinationMode.DEFAULT).isEqualTo(ReadingDestinationMode.EXTERNAL)
    }

    @Test
    fun `destination mode round-trips through its stored id`() {
        assertThat(ReadingDestinationMode.fromStored("IN_APP")).isEqualTo(ReadingDestinationMode.IN_APP)
        assertThat(ReadingDestinationMode.fromStored("EXTERNAL")).isEqualTo(ReadingDestinationMode.EXTERNAL)
    }

    @Test
    fun `an unknown or null destination mode id degrades to the default`() {
        assertThat(ReadingDestinationMode.fromStored("garbage")).isEqualTo(ReadingDestinationMode.EXTERNAL)
        assertThat(ReadingDestinationMode.fromStored(null)).isEqualTo(ReadingDestinationMode.EXTERNAL)
    }

    @Test
    fun `external app default is BLB`() {
        assertThat(ExternalBibleApp.DEFAULT).isEqualTo(ExternalBibleApp.BLB)
    }

    @Test
    fun `bible gateway is the only multi-ref-capable external app and mysword is the only app-required one`() {
        assertThat(ExternalBibleApp.BIBLE_GATEWAY.multiRefCapable).isTrue()
        assertThat(ExternalBibleApp.BLB.multiRefCapable).isFalse()
        assertThat(ExternalBibleApp.YOUVERSION.multiRefCapable).isFalse()
        assertThat(ExternalBibleApp.MYSWORD.requiresApp).isTrue()
        assertThat(ExternalBibleApp.BLB.requiresApp).isFalse()
    }

    @Test
    fun `the legacy IN_APP value is not an external app - it degrades to BLB`() {
        // The old in-app member is gone from this enum; a stored "IN_APP" must not crash and
        // must read as the BLB default (the external app a legacy in-app user falls back to).
        assertThat(ExternalBibleApp.fromStored("IN_APP")).isEqualTo(ExternalBibleApp.BLB)
    }

    @Test
    fun `an unknown or null external app id degrades to BLB`() {
        assertThat(ExternalBibleApp.fromStored("garbage")).isEqualTo(ExternalBibleApp.BLB)
        assertThat(ExternalBibleApp.fromStored(null)).isEqualTo(ExternalBibleApp.BLB)
    }
}

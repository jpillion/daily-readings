package com.jpillion.dailyreadingplanner.core.date

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.datetime.LocalDate
import org.junit.Test

/**
 * M3, permanent half — no `java.time`, so this is the file that moves to `commonTest` in `p2-08`
 * and then runs on every target. Adopted from `spikes/gate0-room-hash` (p1-02).
 *
 * The harness differs from the spike's on purpose: `:app` has no `kotlin-test` dependency, so
 * `kotlin.test.Test` / `assertEquals` were replaced with JUnit 4 + assertk (p1-00 R4). The pinned
 * table, the dates covered and the assertions themselves are unchanged.
 */
class EpochDayPinTest {
    @Test
    fun `kotlinx toEpochDays matches the independently computed pin`() {
        for (p in EpochDayPins.PINS) {
            assertThat(p.date.toEpochDays(), name = "toEpochDays for ${p.iso}").isEqualTo(p.epochDay)
        }
    }

    @Test
    fun `fromEpochDays round-trips every pinned date`() {
        for (p in EpochDayPins.PINS) {
            assertThat(LocalDate.fromEpochDays(p.epochDay), name = "fromEpochDays for ${p.iso}").isEqualTo(p.date)
            assertThat(
                LocalDate.fromEpochDays(p.date.toEpochDays()),
                name = "round-trip for ${p.iso}",
            ).isEqualTo(p.date)
        }
    }

    /** The sign boundary stated explicitly rather than buried in the table. */
    @Test
    fun `epoch day zero is 1970-01-01 and minus one is the day before`() {
        assertThat(LocalDate(1970, 1, 1).toEpochDays()).isEqualTo(0L)
        assertThat(LocalDate(1969, 12, 31).toEpochDays()).isEqualTo(-1L)
        assertThat(LocalDate.fromEpochDays(0L)).isEqualTo(LocalDate(1970, 1, 1))
        assertThat(LocalDate.fromEpochDays(-1L)).isEqualTo(LocalDate(1969, 12, 31))
    }
}

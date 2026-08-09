package com.jpillion.dailyreadingplanner.core.date

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import org.junit.Test

/**
 * M3, JVM-only half — the bridge that proves the ported code keeps every shipped user's
 * `dateEpochDay` values. This one cannot move to `commonTest` (it needs `java.time`) and does not
 * need to: it is a one-time equivalence proof between the OLD implementation and the NEW one.
 *
 * **Keep it for as long as `:app` is a JVM/Android module.** `dateEpochDay` is a component of the
 * progress primary key (`PRIMARY KEY(plan_id, dateEpochDay, stream)`) and of
 * `tracking_start_epoch_day`; rows written by every shipped release up to 1.8.1 were keyed by
 * `java.time.LocalDate.toEpochDay()`, and rows written from 1.9.0 on are keyed by
 * `kotlinx.datetime.LocalDate.toEpochDays()`. Those two numbers agreeing is what makes the upgrade
 * invisible, and this is the only test that compares them.
 *
 * Adopted from `spikes/gate0-room-hash` (p1-02); harness converted to JUnit 4 + assertk because
 * `:app` has no `kotlin-test` dependency. The dates, the sweep and the assertions are unchanged.
 */
class EpochDayJavaTimeEquivalenceTest {
    @Test
    fun `java time toEpochDay equals kotlinx toEpochDays for every pinned date`() {
        for (p in EpochDayPins.PINS) {
            val javaValue =
                java.time.LocalDate
                    .of(p.year, p.month, p.day)
                    .toEpochDay()
            val kotlinxValue = p.date.toEpochDays()
            assertThat(kotlinxValue, name = "java.time vs kotlinx for ${p.iso}").isEqualTo(javaValue)
            assertThat(javaValue, name = "pin vs java.time for ${p.iso}").isEqualTo(p.epochDay)
        }
    }

    @Test
    fun `java time ofEpochDay equals kotlinx fromEpochDays for every pinned date`() {
        for (p in EpochDayPins.PINS) {
            val j = java.time.LocalDate.ofEpochDay(p.epochDay)
            val k = kotlinx.datetime.LocalDate.fromEpochDays(p.epochDay)
            assertThat(k.year, name = "year at ${p.iso}").isEqualTo(j.year)
            assertThat(k.month.number, name = "month at ${p.iso}").isEqualTo(j.monthValue)
            assertThat(k.day, name = "day at ${p.iso}").isEqualTo(j.dayOfMonth)
        }
    }

    /**
     * Not just the pinned set: an exhaustive sweep across the whole displayable span, which is the
     * only way to be sure there is no isolated off-by-one hiding between the pins.
     */
    @Test
    fun `the two implementations agree on every day from 1900 to 2100`() {
        var d = java.time.LocalDate.of(1900, 1, 1)
        val end = java.time.LocalDate.of(2100, 12, 31)
        var checked = 0
        while (d <= end) {
            val k = kotlinx.datetime.LocalDate(d.year, d.monthValue, d.dayOfMonth)
            assertThat(k.toEpochDays(), name = "disagreement at $d").isEqualTo(d.toEpochDay())
            d = d.plusDays(1)
            checked++
        }
        // 1900-01-01..2100-12-31 inclusive.
        assertThat(checked, name = "sweep length changed — update the expectation deliberately")
            .isEqualTo(73_414)
    }
}

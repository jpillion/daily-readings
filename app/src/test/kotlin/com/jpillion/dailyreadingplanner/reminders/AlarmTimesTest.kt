package com.jpillion.dailyreadingplanner.reminders

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** S12: the pure scheduling math — next reminder occurrence and next midnight. */
class AlarmTimesTest {
    private val zone = ZoneId.of("America/New_York")

    private fun at(
        hour: Int,
        minute: Int,
        second: Int = 0,
    ): ZonedDateTime = ZonedDateTime.of(2026, 6, 10, hour, minute, second, 0, zone)

    @Test
    fun `a time later today schedules today`() {
        val next = AlarmTimes.nextOccurrence(at(7, 30), LocalTime.of(8, 0))
        assertThat(next).isEqualTo(at(8, 0))
    }

    @Test
    fun `a time already past today schedules tomorrow`() {
        val next = AlarmTimes.nextOccurrence(at(9, 0), LocalTime.of(8, 0))
        assertThat(next).isEqualTo(at(8, 0).plusDays(1))
    }

    @Test
    fun `exactly-now schedules tomorrow, never today again - the no-refire-loop boundary`() {
        // The alarm just fired at its target instant; rescheduling "today" would refire
        // immediately. Strict isAfter is the load-bearing comparison (mutation target M-S12-3).
        val next = AlarmTimes.nextOccurrence(at(8, 0), LocalTime.of(8, 0))
        assertThat(next).isEqualTo(at(8, 0).plusDays(1))
    }

    @Test
    fun `one second past the target also lands tomorrow`() {
        val next = AlarmTimes.nextOccurrence(at(8, 0, second = 1), LocalTime.of(8, 0))
        assertThat(next).isEqualTo(at(8, 0).plusDays(1))
    }

    @Test
    fun `next midnight is the start of tomorrow in the same zone`() {
        val next = AlarmTimes.nextMidnight(at(23, 59))
        assertThat(next).isEqualTo(ZonedDateTime.of(2026, 6, 11, 0, 0, 0, 0, zone))
    }

    @Test
    fun `at exactly midnight the next midnight is tomorrow's, not now`() {
        val midnight = ZonedDateTime.of(2026, 6, 10, 0, 0, 0, 0, zone)
        assertThat(AlarmTimes.nextMidnight(midnight))
            .isEqualTo(ZonedDateTime.of(2026, 6, 11, 0, 0, 0, 0, zone))
    }

    @Test
    fun `next occurrence crosses month and year boundaries on real dates`() {
        val newYearsEve = ZonedDateTime.of(2026, 12, 31, 22, 0, 0, 0, zone)
        assertThat(AlarmTimes.nextOccurrence(newYearsEve, LocalTime.of(8, 0)))
            .isEqualTo(ZonedDateTime.of(2027, 1, 1, 8, 0, 0, 0, zone))
    }

    @Test
    fun `the persistent refresh time is 01-00 local - owner specified`() {
        assertThat(AlarmTimes.PERSISTENT_REFRESH_TIME).isEqualTo(LocalTime.of(1, 0))
    }
}

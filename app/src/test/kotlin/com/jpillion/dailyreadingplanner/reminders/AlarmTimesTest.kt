package com.jpillion.dailyreadingplanner.reminders

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.junit.Test
import kotlin.time.Instant

/** S12: the pure scheduling math — next reminder occurrence and next midnight. */
class AlarmTimesTest {
    private val zone = TimeZone.of("America/New_York")

    private fun at(
        hour: Int,
        minute: Int,
        second: Int = 0,
    ): Instant = LocalDateTime(2026, 6, 10, hour, minute, second).toInstant(zone)

    private fun nyInstant(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Instant = LocalDateTime(year, month, day, hour, minute).toInstant(zone)

    @Test
    fun `a time later today schedules today`() {
        val next = AlarmTimes.nextOccurrence(at(7, 30), zone, LocalTime(8, 0))
        assertThat(next).isEqualTo(at(8, 0))
    }

    @Test
    fun `a time already past today schedules tomorrow`() {
        val next = AlarmTimes.nextOccurrence(at(9, 0), zone, LocalTime(8, 0))
        assertThat(next).isEqualTo(nyInstant(2026, 6, 11, 8, 0))
    }

    @Test
    fun `exactly-now schedules tomorrow - never today again - the no-refire-loop boundary`() {
        // The alarm just fired at its target instant; rescheduling "today" would refire
        // immediately. The strict comparison is the load-bearing one (mutation target M-S12-3).
        val next = AlarmTimes.nextOccurrence(at(8, 0), zone, LocalTime(8, 0))
        assertThat(next).isEqualTo(nyInstant(2026, 6, 11, 8, 0))
    }

    @Test
    fun `one second past the target also lands tomorrow`() {
        val next = AlarmTimes.nextOccurrence(at(8, 0, second = 1), zone, LocalTime(8, 0))
        assertThat(next).isEqualTo(nyInstant(2026, 6, 11, 8, 0))
    }

    @Test
    fun `next midnight is the start of tomorrow in the same zone`() {
        val next = AlarmTimes.nextMidnight(at(23, 59), zone)
        assertThat(next).isEqualTo(nyInstant(2026, 6, 11, 0, 0))
    }

    @Test
    fun `at exactly midnight the next midnight is tomorrow's - not now`() {
        val midnight = nyInstant(2026, 6, 10, 0, 0)
        assertThat(AlarmTimes.nextMidnight(midnight, zone)).isEqualTo(nyInstant(2026, 6, 11, 0, 0))
    }

    @Test
    fun `next occurrence crosses month and year boundaries on real dates`() {
        val newYearsEve = nyInstant(2026, 12, 31, 22, 0)
        assertThat(AlarmTimes.nextOccurrence(newYearsEve, zone, LocalTime(8, 0)))
            .isEqualTo(nyInstant(2027, 1, 1, 8, 0))
    }

    @Test
    fun `the persistent refresh time is 01-00 local - owner specified`() {
        assertThat(AlarmTimes.PERSISTENT_REFRESH_TIME).isEqualTo(LocalTime(1, 0))
    }

    /**
     * p1-02, and the reason [AlarmTimes] takes an [Instant] rather than a wall-clock
     * `LocalDateTime`.
     *
     * DST fall-back in New York on 2026-11-01: 02:00 EDT (UTC-4) rewinds to 01:00 EST (UTC-5), so
     * every wall-clock time from 01:00 to 01:59 happens **twice**. Here "now" is the *second*
     * 01:30 (06:30 UTC) and the reminder is set for 01:45.
     *
     * On the clock face 01:45 is still ahead, so a `LocalDateTime` comparison would schedule
     * "today" — an instant (05:45 UTC, the *first* 01:45) that is already 45 minutes in the past,
     * and `setAndAllowWhileIdle` fires a past trigger immediately. Comparing instants, as
     * `ZonedDateTime.isAfter` did before the port and as the ported code still does, correctly
     * skips to tomorrow.
     *
     * This test goes red against a wall-clock rewrite and green against the shipped semantics; it
     * is the only coverage of that difference in the suite.
     */
    @Test
    fun `during a DST fall-back a repeated wall-clock time does not schedule a past alarm`() {
        val firstOneThirty = Instant.parse("2026-11-01T05:30:00Z")
        val secondOneThirty = Instant.parse("2026-11-01T06:30:00Z")
        // Pin the premise itself: these two instants really are the same wall-clock minute.
        assertThat(secondOneThirty - firstOneThirty).isEqualTo(kotlin.time.Duration.parse("1h"))

        val next = AlarmTimes.nextOccurrence(secondOneThirty, zone, LocalTime(1, 45))

        assertThat(next).isEqualTo(nyInstant(2026, 11, 2, 1, 45))
    }

    /** The same boundary from the other side: before the rewind, 01:45 today is genuinely next. */
    @Test
    fun `before a DST fall-back the same time still schedules today`() {
        val beforeTheRewind = Instant.parse("2026-11-01T05:00:00Z")
        val next = AlarmTimes.nextOccurrence(beforeTheRewind, zone, LocalTime(1, 45))
        assertThat(next).isEqualTo(Instant.parse("2026-11-01T05:45:00Z"))
    }

    /** DST spring-forward: the day still has a midnight, and it is the next one. */
    @Test
    fun `next midnight is correct across a spring-forward date`() {
        val eveOfTheGap = nyInstant(2026, 3, 7, 23, 30)
        assertThat(AlarmTimes.nextMidnight(eveOfTheGap, zone)).isEqualTo(nyInstant(2026, 3, 8, 0, 0))
    }

    @Test
    fun `a reminder set inside the spring-forward gap resolves forward - never backward`() {
        // 02:30 does not exist on 2026-03-08 in New York. Both java.time's ZonedDateTime.of and
        // kotlinx's toInstant shift such a time forward by the gap, to 03:30 EDT = 07:30 UTC.
        val justBeforeTheGap = nyInstant(2026, 3, 8, 1, 0)
        val next = AlarmTimes.nextOccurrence(justBeforeTheGap, zone, LocalTime(2, 30))
        assertThat(next).isEqualTo(Instant.parse("2026-03-08T07:30:00Z"))
    }
}

package com.jpillion.dailyreadingplanner.reminders

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Pure alarm-time math (S12, D-S12-4): the scheduling *decisions* live here on the JVM;
 * only the thin [AlarmManagerReminderScheduler] touches the platform.
 *
 * p1-02 — how the `java.time.ZonedDateTime` signatures were ported, and why it is not the obvious
 * shape. A `ZonedDateTime` is an instant *and* a zone *and* a cached local date-time in one value;
 * kotlinx-datetime deliberately has no such type, so the pair is passed explicitly as
 * ([now], [zone]) and returned as an [Instant] — which is what both callers actually wanted
 * (`trigger.toInstant().toEpochMilli()`).
 *
 * **The comparison had to stay on instants.** Rewriting `todayCandidate.isAfter(now)` as a
 * wall-clock `LocalDateTime` comparison looks equivalent and is not: during a DST fall-back the
 * same wall-clock time occurs twice, so a reminder set inside the repeated hour would compare
 * "later" on the clock while being *earlier* as an instant — scheduling an already-past alarm that
 * `setAndAllowWhileIdle` fires immediately. `ZonedDateTime.isAfter` compares epoch seconds; so does
 * this.
 *
 * The two local→instant resolutions are the exact analogues of what they replaced:
 * `LocalDateTime.toInstant(zone)` matches `ZonedDateTime.of` (overlap → the earlier offset, gap →
 * shifted forward), and `LocalDate.atStartOfDayIn(zone)` matches `ChronoLocalDate.atStartOfDay`
 * (the first valid moment of the day when midnight itself does not exist).
 */
object AlarmTimes {
    /** The persistent-notification refresh time (S21, D-S21-3): 01:00 local, owner-specified. */
    val PERSISTENT_REFRESH_TIME: LocalTime = LocalTime(1, 0)

    /**
     * The next occurrence of [timeOfDay] in [zone] strictly after [now]. "Strictly" is
     * load-bearing: when an alarm fires exactly at its target time, the reschedule must
     * land tomorrow, never today again (no refire loop).
     */
    fun nextOccurrence(
        now: Instant,
        zone: TimeZone,
        timeOfDay: LocalTime,
    ): Instant {
        val localDate = now.toLocalDateTime(zone).date
        val todayCandidate = localDate.atTime(timeOfDay).toInstant(zone)
        return if (todayCandidate > now) {
            todayCandidate
        } else {
            localDate
                .plus(1, DateTimeUnit.DAY)
                .atTime(timeOfDay)
                .toInstant(zone)
        }
    }

    /** The next local midnight strictly after [now] — the widget's date-rollover moment (FR-23). */
    fun nextMidnight(
        now: Instant,
        zone: TimeZone,
    ): Instant =
        now
            .toLocalDateTime(zone)
            .date
            .plus(1, DateTimeUnit.DAY)
            .atStartOfDayIn(zone)
}

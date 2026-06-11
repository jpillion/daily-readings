package com.jpillion.dailyreadingplanner.reminders

import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Pure alarm-time math (S12, D-S12-4): the scheduling *decisions* live here on the JVM;
 * only the thin [AlarmManagerReminderScheduler] touches the platform.
 */
object AlarmTimes {
    /**
     * The next wall-clock occurrence of [timeOfDay] strictly after [now]. "Strictly" is
     * load-bearing: when an alarm fires exactly at its target time, the reschedule must
     * land tomorrow, never today again (no refire loop).
     */
    fun nextOccurrence(
        now: ZonedDateTime,
        timeOfDay: LocalTime,
    ): ZonedDateTime {
        val todayCandidate = now.toLocalDate().atTime(timeOfDay).atZone(now.zone)
        return if (todayCandidate.isAfter(now)) {
            todayCandidate
        } else {
            now
                .toLocalDate()
                .plusDays(1)
                .atTime(timeOfDay)
                .atZone(now.zone)
        }
    }

    /** The next local midnight strictly after [now] — the widget's date-rollover moment (FR-23). */
    fun nextMidnight(now: ZonedDateTime): ZonedDateTime = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
}

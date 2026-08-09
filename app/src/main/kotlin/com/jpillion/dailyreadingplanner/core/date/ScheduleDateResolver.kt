package com.jpillion.dailyreadingplanner.core.date

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import javax.inject.Inject

/** Result of resolving a real calendar date against the 365-day plan (ESpec §5.4). */
sealed interface ResolvedDate {
    /** A normal day: look up [readingDate] in the plan for exactly 3 portions. */
    data class Scheduled(
        val readingDate: ReadingDate,
    ) : ResolvedDate

    /** Feb 29 (leap years only): no readings, no marks, no progress key (decision D1). */
    data object NoScheduledReadings : ResolvedDate
}

/**
 * Maps the device-local [LocalDate] to the plan's (month, day) key. The single special case
 * is Feb 29 → [ResolvedDate.NoScheduledReadings]; every other date is [ResolvedDate.Scheduled].
 * Pure and total — no fold/double-day logic (decision D1).
 */
class ScheduleDateResolver
    @Inject
    constructor() {
        fun resolve(date: LocalDate): ResolvedDate =
            if (date.month.number == 2 && date.day == 29) {
                ResolvedDate.NoScheduledReadings
            } else {
                ResolvedDate.Scheduled(ReadingDate(date.month.number, date.day))
            }
    }

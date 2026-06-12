package com.jpillion.dailyreadingplanner.domain.model

/**
 * One day of one stream's year strip (S17). Derived ONLY through [DayCompletion] /
 * `DayCompletionClassifier` (D-S17-2) — never re-derived: READ = the stream is marked for
 * that day (including pre-tracking-start days — earned-green parity), MISSED = a past,
 * post-start, unmarked day, NEUTRAL = everything else (Feb 29, pre-start, today in grace,
 * future).
 */
enum class StripDayState { READ, MISSED, NEUTRAL }

/**
 * The year-strip visualization model (S17): for each stream, one [StripDayState] per
 * calendar day of [year] — `lengthOfYear` entries (366 in a leap year; Feb 29 is NEUTRAL
 * and visually indistinguishable, D-S17-4). [todayIndex] is today's 0-based position in
 * the arrays (the strip's orientation tick); null only if today falls outside [year].
 */
data class YearStrips(
    val year: Int,
    val todayIndex: Int?,
    val dayStates: Map<Stream, List<StripDayState>>,
) {
    /** Total segments per strip: 365, or 366 in a leap year. */
    val dayCount: Int get() = dayStates.values.firstOrNull()?.size ?: 0
}

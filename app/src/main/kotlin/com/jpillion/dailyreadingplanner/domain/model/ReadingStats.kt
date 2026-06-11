package com.jpillion.dailyreadingplanner.domain.model

/**
 * The Stats screen's four stat groups (PRD §13.1, FR-15), derived live from stored marks —
 * never stored counters (R-STREAK-6). Denominators are plan constants: the Bible Companion
 * is 365 days x 3 streams regardless of leap years (Feb 29 has no readings, D1).
 */
data class ReadingStats(
    /** Days in the streak ending now; an incomplete today is in grace (R-STREAK-3). */
    val currentStreakDays: Int,
    /** Longest run ever, all-time across all stored years (R-STREAK-4). */
    val longestStreakDays: Int,
    /** Readings marked in the current calendar year, of [YEAR_TOTAL_READINGS]. */
    val yearReadCount: Int,
    /** Days marked per stream in the current calendar year, of [STREAM_TOTAL_DAYS] each. */
    val streamReadCounts: Map<Stream, Int>,
) {
    companion object {
        /** 365 plan days x 3 streams — the full-year denominator (PRD §13.1 group 3). */
        const val YEAR_TOTAL_READINGS = 1_095

        /** Each stream has one portion on each of the plan's 365 days. */
        const val STREAM_TOTAL_DAYS = 365
    }
}

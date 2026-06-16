package com.jpillion.dailyreadingplanner.domain.model

/**
 * The Stats screen's four stat groups (PRD §13.1, FR-15), derived live from stored marks —
 * never stored counters (R-STREAK-6).
 *
 * D-ALT-7 (Alt Sprint C): the denominators are now INSTANCE fields computed from the active
 * plan's descriptor — [yearTotalReadings] = `dayCount × streamCount`, [streamTotalDays] =
 * `dayCount`. For the Bible Companion these are 1,095 and 365 exactly (parity); for M'Cheyne
 * (N=4) the year total is 1,460; for a single-stream plan it is 365. The floor-rounding percent
 * rule (D-S11-4 — 100% only at completion) is unchanged; it just divides by the plan's real total.
 *
 * D-ALT-8/22: [streams] is the active plan's ordered stream identity (number + title); the
 * per-stream rows iterate it (never the retired enum), reading the title as data.
 */
data class ReadingStats(
    /** Days in the streak ending now; an incomplete today is in grace (R-STREAK-3). */
    val currentStreakDays: Int,
    /** Longest run ever, all-time across all stored years (R-STREAK-4). */
    val longestStreakDays: Int,
    /** Readings marked in the current calendar year, of [yearTotalReadings]. */
    val yearReadCount: Int,
    /** Days marked per stream number in the current calendar year, of [streamTotalDays] each. */
    val streamReadCounts: Map<Int, Int>,
    /** The active plan's ordered streams (number + title) — the per-stream rows iterate this. */
    val streams: List<StreamDescriptor>,
    /** `dayCount × streamCount` — the full-year denominator (PRD §13.1 group 3). */
    val yearTotalReadings: Int,
    /** `dayCount` — each stream has one portion on each scheduled day. */
    val streamTotalDays: Int,
)

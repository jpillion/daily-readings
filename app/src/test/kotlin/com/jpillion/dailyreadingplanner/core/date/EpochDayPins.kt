package com.jpillion.dailyreadingplanner.core.date

import kotlinx.datetime.LocalDate

/**
 * M3 — the pinned epoch-day table.
 *
 * `dateEpochDay` is the primary-key component of every progress row a shipped user owns
 * (`PRIMARY KEY(plan_id, dateEpochDay, stream)`), so a one-day disagreement between
 * `java.time.LocalDate.toEpochDays()` and `kotlinx.datetime.LocalDate.toEpochDays()` would silently
 * shift every user's reading history after the port — no crash, no red test unless this one exists.
 *
 * **The expected values were computed independently of BOTH implementations under test** (proleptic
 * Gregorian day arithmetic, days since 1970-01-01), so this table is a third witness rather than a
 * recording of whatever one library happens to return.
 *
 * Adopted by `p1-02` as-is: no `java.time` dependency, so `p2-08` moves it to `commonTest` and it
 * runs on every target. The JVM-only equivalence check against `java.time` lives separately in
 * [EpochDayJavaTimeEquivalenceTest] — that one cannot move to common, and does not need to.
 */
data class EpochDayPin(
    val year: Int,
    val month: Int,
    val day: Int,
    val epochDay: Long,
) {
    val date: LocalDate get() = LocalDate(year, month, day)
    val iso: String get() = "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}

object EpochDayPins {
    private fun pin(
        y: Int,
        m: Int,
        d: Int,
        e: Long,
    ) = EpochDayPin(y, m, d, e)

    /**
     * Covers every case the brief names: the sign boundary, leap days, the non-leap century,
     * the year boundary the day pager crosses, a pre-1970 spread, the day pager's actual
     * ±`DAY_WINDOW` reach (`DayReadingsScreen.kt:57` — `DAY_WINDOW = 10_000`, verified against
     * `main`, measured from 2026-08-08), and the Jan-1/Dec-31 endpoints of a spread of plan years.
     */
    val PINS: List<EpochDayPin> =
        listOf(
            // The sign boundary.
            pin(1970, 1, 1, 0L),
            pin(1969, 12, 31, -1L),
            // Leap days — Feb 29 is a first-class case in this product (no scheduled readings).
            pin(2000, 2, 29, 11016L),
            pin(2024, 2, 29, 19782L),
            // The non-leap century: 1900 is NOT a leap year, 2000 IS.
            pin(1900, 2, 28, -25509L),
            pin(1900, 3, 1, -25508L),
            // Today, and the year boundary the day pager crosses (Dec 31 -> Jan 1).
            pin(2026, 8, 8, 20673L),
            pin(2026, 12, 31, 20818L),
            pin(2027, 1, 1, 20819L),
            // Pre-1970 spread.
            pin(1900, 1, 1, -25567L),
            pin(1955, 6, 15, -5314L),
            // The day pager's real reach: 2026-08-08 -/+ DAY_WINDOW (10,000) days.
            pin(1999, 3, 23, 10673L),
            pin(2053, 12, 24, 30673L),
            // Plan-year endpoints across the displayable span.
            pin(1970, 12, 31, 364L),
            pin(2000, 1, 1, 10957L),
            pin(2000, 12, 31, 11322L),
            pin(2024, 1, 1, 19723L),
            pin(2024, 12, 31, 20088L),
            pin(2026, 1, 1, 20454L),
            pin(2027, 12, 31, 21183L),
            pin(2100, 1, 1, 47482L),
            pin(2100, 12, 31, 47846L),
        )
}

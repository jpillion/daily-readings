package com.jpillion.dailyreadingplanner.core.date

/**
 * A schedule lookup key: the plan is anchored by (month, day), identical for every reader
 * worldwide (ESpec §5.4). Feb 29 is intentionally NOT representable here — the resolver
 * returns a no-readings state for it before a [ReadingDate] is ever constructed.
 */
data class ReadingDate(
    val month: Int,
    val day: Int,
) {
    init {
        require(month in 1..12) { "month $month out of range" }
        require(day in 1..DAYS_PER_MONTH[month - 1]) { "day $day out of range for month $month" }
        require(!(month == 2 && day == 29)) { "Feb 29 has no scheduled readings (decision D1)" }
    }

    companion object {
        private val DAYS_PER_MONTH = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    }
}

package com.jpillion.dailyreadingplanner.domain.model

/**
 * A calendar day's completion state for at-a-glance surfaces (the date-picker indicators, S8):
 * - [COMPLETE]: all three streams marked — past, today, or future alike (green).
 * - [MISSED]: a *past* scheduled day with fewer than three marks (red).
 * - [NONE]: no indicator — an incomplete today/future day, or Feb 29 (no scheduled readings, D1).
 */
enum class DayCompletion {
    COMPLETE,
    MISSED,
    NONE,
}

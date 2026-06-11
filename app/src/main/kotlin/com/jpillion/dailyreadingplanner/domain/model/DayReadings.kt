package com.jpillion.dailyreadingplanner.domain.model

import java.time.LocalDate

/** One scheduled reading plus whether the user has marked it read on this exact date. */
data class ReadingStatus(
    val portion: Portion,
    val isRead: Boolean,
)

/**
 * Everything the UI needs for one calendar day. Feb 29 is modeled explicitly as
 * [NoScheduledReadings] (decision D1) — never as a scheduled day with an empty list.
 */
sealed interface DayReadings {
    val date: LocalDate

    data class Scheduled(
        override val date: LocalDate,
        val readings: List<ReadingStatus>,
        val dayComplete: Boolean,
    ) : DayReadings

    /** Leap-day state: no readings, no mark controls, no progress tracked. */
    data class NoScheduledReadings(
        override val date: LocalDate,
    ) : DayReadings
}

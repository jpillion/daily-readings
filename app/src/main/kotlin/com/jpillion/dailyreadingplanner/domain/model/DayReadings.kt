package com.jpillion.dailyreadingplanner.domain.model

import java.time.LocalDate

/**
 * One scheduled reading plus whether the user has marked it read on this exact date.
 *
 * D-ALT-22/23 (Alt Sprint C, D-C-6): [streamTitle] is the active plan's title for this portion's
 * stream, resolved once in the use-case layer from the active descriptor — the UI reads it as data
 * and never looks anything up. `null` for a single-stream plan (a lone reading needs no "which
 * stream" label, D-ALT-23); the card/widget then render the reference alone.
 */
data class ReadingStatus(
    val portion: Portion,
    val isRead: Boolean,
    val streamTitle: String? = null,
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

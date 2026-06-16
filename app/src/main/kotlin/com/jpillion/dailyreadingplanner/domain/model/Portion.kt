package com.jpillion.dailyreadingplanner.domain.model

/**
 * One day's reading from one stream: an ordered list of chapter refs. A portion may span
 * two books (Jun 19 / Dec 19 = 2 John + 3 John) — never assume refs share a book.
 *
 * D-ALT-5 (Alt Sprint C): a within-day track is identified by its plain [streamNumber] (1..N) —
 * the value already persisted as the progress `stream` column and the plan JSON `stream` field.
 * The former 3-value `Stream` enum is retired; the display *title* now comes from the active
 * plan's `StreamDescriptor.title` (D-ALT-22), never from a per-portion field.
 */
data class Portion(
    val streamNumber: Int,
    val refs: List<Reference>,
) {
    init {
        require(refs.isNotEmpty()) { "portion for stream $streamNumber has no refs" }
    }

    /** The chapter a tap opens on BLB (PRD flow U3: multi-chapter portions open the first). */
    val firstRef: Reference get() = refs.first()
}

package com.jpillion.dailyreadingplanner.domain.model

/**
 * One day's reading from one stream: an ordered list of chapter refs. A portion may span
 * two books (Jun 19 / Dec 19 = 2 John + 3 John) — never assume refs share a book.
 */
data class Portion(
    val stream: Stream,
    val refs: List<Reference>,
) {
    init {
        require(refs.isNotEmpty()) { "portion for $stream has no refs" }
    }

    /** The chapter a tap opens on BLB (PRD flow U3: multi-chapter portions open the first). */
    val firstRef: Reference get() = refs.first()
}

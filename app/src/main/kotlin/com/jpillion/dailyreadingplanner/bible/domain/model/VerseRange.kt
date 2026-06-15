package com.jpillion.dailyreadingplanner.bible.domain.model

/**
 * VB-T1 / ESpec-v3 §5.1 — an inclusive canonical verse_id range [startVerseId, endVerseId].
 * The seam ([com.jpillion.dailyreadingplanner.bible.domain.BibleTextSource]) is addressed by
 * these; the resolver guarantees only well-formed ranges (start <= end) ever reach the source.
 */
data class VerseRange(
    val startVerseId: Long,
    val endVerseId: Long,
) {
    init {
        require(startVerseId <= endVerseId) {
            "verse range start $startVerseId is after end $endVerseId"
        }
    }
}

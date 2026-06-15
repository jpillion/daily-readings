package com.jpillion.dailyreadingplanner.bible.domain

import com.jpillion.dailyreadingplanner.bible.domain.model.VerseRange
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseText

/**
 * VB-T2 / ESpec-v3 §4.1, D-V3-3 — the single seam between the version-agnostic spine and whatever
 * text artifact is loaded. One method, range-addressed. Everything above this interface is
 * indifferent to the text version; everything below it is an encapsulated, swappable file.
 */
interface BibleTextSource {
    /**
     * Verses whose canonical id ∈ [range.startVerseId, range.endVerseId], in ascending id order.
     * Empty list for a range with no rows (the resolver guarantees only valid ranges reach here).
     */
    suspend fun getVerses(range: VerseRange): List<VerseText>
}

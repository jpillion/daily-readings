package com.jpillion.dailyreadingplanner.bible.domain

import com.jpillion.dailyreadingplanner.bible.domain.model.BibleTranslation
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseRange
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseText

/**
 * VB-T2 / ESpec-v3 §4.1, D-V3-3 — the single seam between the version-agnostic spine and whatever
 * text artifact is loaded. Range-addressed. Everything above this interface is indifferent to the
 * text version; everything below it is an encapsulated, swappable file.
 */
interface BibleTextSource {
    /**
     * Verses whose canonical id ∈ [range.startVerseId, range.endVerseId], in ascending id order.
     * Empty list for a range with no rows (the resolver guarantees only valid ranges reach here).
     */
    suspend fun getVerses(range: VerseRange): List<VerseText>

    /**
     * The text versions bundled in the loaded artifact, in `id` order (D-N-1). The reader's version
     * label comes from here, never a hardcoded literal — today the asset's `translation` table holds
     * exactly one row (KJV / "King James Version"). When a future artifact bundles more, the reader's
     * version control becomes a dropdown over this list (D-N-3).
     */
    suspend fun translations(): List<BibleTranslation>
}

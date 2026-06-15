package com.jpillion.dailyreadingplanner.domain.model

import com.jpillion.dailyreadingplanner.data.reference.Book

/**
 * A single chapter reference, with the book already resolved against the catalog — unknown
 * book names fail at plan-load time, never at display or URL-build time.
 *
 * [verses] is an optional chapter-relative verse window (schema v2 / D-MODEL-1). `null` ⇒ the
 * whole chapter (the default; 1,090+ readings are unchanged). Only the four Psalm-119 days carry
 * one. It is deliberately NOT the bible spine's `VerseRange` (absolute verse_ids): this is the
 * PLANNER domain, which must not depend on the `bible/` package. The conversion to a verse_id
 * range happens in `PortionVerseBridge` (the planner↔bible join), not here.
 */
data class Reference(
    val book: Book,
    val chapter: Int,
    val verses: ReferenceVerses? = null,
) {
    init {
        require(chapter in 1..book.chapterCount) {
            "${book.canonicalName} has ${book.chapterCount} chapters; chapter $chapter out of range"
        }
    }
}

/**
 * A chapter-relative, 1-based, inclusive verse window [start, end] (D-MODEL-1). The per-chapter
 * upper bound (`end <= chapterVerseCount`) is NOT enforced here — the planner domain has no
 * verse-count table; that bound is proven by the plan verification gate against the committed
 * KJV verse-count witness, exactly as the chapter-range bound is gate-proven, not model-proven.
 */
data class ReferenceVerses(
    val start: Int,
    val end: Int,
) {
    init {
        require(start in 1..end) {
            "verse window start $start must be >= 1 and <= end $end"
        }
    }
}

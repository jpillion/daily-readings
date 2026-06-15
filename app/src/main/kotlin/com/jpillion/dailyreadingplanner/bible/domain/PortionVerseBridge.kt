package com.jpillion.dailyreadingplanner.bible.domain

import com.jpillion.dailyreadingplanner.bible.domain.model.VerseId
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseRange
import com.jpillion.dailyreadingplanner.domain.model.Portion
import javax.inject.Inject

/**
 * VB-T6 / ESpec-v3 §5.4, FR-V3-2 — the MVP connective tissue between the chapter-keyed plan
 * (`Portion = List<Reference>`) and the verse-keyed text. Maps a portion to an ordered list of
 * whole-chapter verse ranges the reader renders in sequence.
 *
 * Each Reference maps INDEPENDENTLY via [VerseId.chapterRange] (book.order, chapter) — it NEVER
 * assumes the refs share a book, so the two-book portion (2 John 1; 3 John 1) yields two ranges
 * across two books for free. Order is preserved exactly as the plan lists the refs.
 */
class PortionVerseBridge
    @Inject
    constructor() {
        fun rangesFor(portion: Portion): List<VerseRange> =
            portion.refs.map { VerseId.chapterRange(it.book.order, it.chapter) }
    }

package com.jpillion.dailyreadingplanner.bible.domain

import com.jpillion.dailyreadingplanner.bible.domain.model.VerseId
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseRange
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.Reference
import javax.inject.Inject

/**
 * VB-T6 / ESpec-v3 §5.4, FR-V3-2 — the MVP connective tissue between the chapter-keyed plan
 * (`Portion = List<Reference>`) and the verse-keyed text. Maps a portion to an ordered list of
 * verse ranges the reader renders in sequence.
 *
 * Each Reference maps INDEPENDENTLY (D-READER-1) — it NEVER assumes the refs share a book, so the
 * two-book portion (2 John 1; 3 John 1) yields two ranges across two books for free. Order is
 * preserved exactly as the plan lists the refs.
 *
 * A whole-chapter ref → the whole-chapter range (incl. verse-0 superscription). A ref carrying a
 * verse window ([Reference.verses], schema v2 / the four Psalm-119 days) → exactly that window,
 * `[encode(book, chapter, start) … encode(book, chapter, end)]` — so the reader shows only verses
 * start..end. This is the single seam that turns a windowed ref into a verse_id range; the
 * planner-domain `ReferenceVerses` becomes a `VerseRange` here, where bible code is allowed.
 */
class PortionVerseBridge
    @Inject
    constructor() {
        fun rangesFor(portion: Portion): List<VerseRange> = portion.refs.map(::rangeFor)

        private fun rangeFor(ref: Reference): VerseRange {
            val verses = ref.verses ?: return VerseId.chapterRange(ref.book.order, ref.chapter)
            return VerseRange(
                startVerseId = VerseId.encode(ref.book.order, ref.chapter, verses.start),
                endVerseId = VerseId.encode(ref.book.order, ref.chapter, verses.end),
            )
        }
    }

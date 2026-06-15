package com.jpillion.dailyreadingplanner.bible.domain.model

import com.jpillion.dailyreadingplanner.data.reference.Book

/**
 * VB-T3 / ESpec-v3 §5.2, D-V3-9 — a verse-level reference, distinct from the chapter-level
 * `domain.model.Reference` (which the planner owns and which has no verse component). The reader
 * needs the verse-0 superscription invariant.
 *
 * THE TRAP (FR-V3-3): the verse guard MUST be `verse in 0..999`, NEVER `verse >= 1` — a `>= 1`
 * lower bound silently drops every Psalm title (which lives at verse 0). Pinned by VerseRefTest
 * and mutation-verified.
 */
data class VerseRef(
    val book: Book,
    val chapter: Int,
    val verse: Int,
) {
    init {
        require(chapter in 1..book.chapterCount) {
            "${book.canonicalName} ch $chapter out of range (1..${book.chapterCount})"
        }
        require(verse in 0..999) { "verse $verse out of [0,999]" } // 0 = superscription
    }

    val verseId: Long get() = VerseId.encode(book.order, chapter, verse)
}

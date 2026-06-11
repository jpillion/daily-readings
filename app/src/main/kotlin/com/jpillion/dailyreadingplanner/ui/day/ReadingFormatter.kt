package com.jpillion.dailyreadingplanner.ui.day

import com.jpillion.dailyreadingplanner.data.reference.Book
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.Reference
import com.jpillion.dailyreadingplanner.domain.model.Stream

/**
 * Presentation formatting for portions and streams (FR-13). Consecutive chapters of one
 * book collapse to a range ("Genesis 1–2", en dash); runs are joined with "; " so a
 * multi-book portion reads "2 John 1; 3 John 1" (Jun 19 / Dec 19 — never assume one book).
 *
 * [formatAbbreviated] is the same collapse with [Book.displayAbbrev] names ("Gen 1–2",
 * "2Jo 1; 3Jo 1") for width-constrained surfaces — the 1x1/1x2 widget (D-S9-1).
 *
 * Plain strings, not resources: book names are canonical data (V1 is English/KJV-only);
 * revisit if localization ever lands.
 */
object ReadingFormatter {
    fun streamTitle(stream: Stream): String =
        when (stream) {
            Stream.LAW_AND_HISTORY -> "Law & History"
            Stream.PSALMS_AND_PROPHECY -> "Psalms & Prophecy"
            Stream.NEW_TESTAMENT -> "New Testament"
        }

    fun format(portion: Portion): String =
        consecutiveRuns(portion.refs).joinToString("; ") { formatRun(it, Book::canonicalName) }

    fun formatAbbreviated(portion: Portion): String =
        consecutiveRuns(portion.refs).joinToString("; ") { formatRun(it, Book::displayAbbrev) }

    /** Splits refs into runs of the same book with consecutive chapters, preserving order. */
    private fun consecutiveRuns(refs: List<Reference>): List<List<Reference>> {
        val runs = mutableListOf<MutableList<Reference>>()
        for (ref in refs) {
            val current = runs.lastOrNull()
            val previous = current?.last()
            if (previous != null && previous.book == ref.book && previous.chapter + 1 == ref.chapter) {
                current += ref
            } else {
                runs += mutableListOf(ref)
            }
        }
        return runs
    }

    private fun formatRun(
        run: List<Reference>,
        bookName: (Book) -> String,
    ): String {
        val book = bookName(run.first().book)
        return if (run.size == 1) {
            "$book ${run.first().chapter}"
        } else {
            "$book ${run.first().chapter}–${run.last().chapter}"
        }
    }
}

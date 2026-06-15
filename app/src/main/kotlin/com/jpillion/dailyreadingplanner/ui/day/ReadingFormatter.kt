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
 * A ref carrying a verse window ([Reference.verses], schema v2 / the four Psalm-119 days) renders
 * "Psalm 119:1–40" (en dash; abbreviated "Psa 119:1–40"); a single-verse window → "Psalm 119:1"
 * (D-UI-1). A windowed ref is always its own run (it never merges with a chapter neighbour).
 *
 * Psalms singular/plural (D-UI-2, owner request): the only book whose canonical name has a
 * singular form. The full form renders "Psalm" when a run covers exactly ONE chapter
 * (single chapter, with or without a verse window — "Psalm 23", "Psalm 119:1–40") and
 * "Psalms" when it spans MORE than one chapter ("Psalms 1–2"). No other book changes. The
 * abbreviated form keeps "Psa" for both (reads fine either way — pinned unchanged).
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
            val canMerge =
                previous != null &&
                    previous.book == ref.book &&
                    previous.chapter + 1 == ref.chapter &&
                    previous.verses == null &&
                    ref.verses == null
            if (canMerge) {
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
        // A run of size 1 is exactly one chapter (plain or verse-windowed); size > 1 spans chapters.
        val singleChapter = run.size == 1
        val book = displayBookName(run.first().book, bookName, singleChapter)
        return if (singleChapter) {
            val ref = run.first()
            val verses = ref.verses
            when {
                verses == null -> "$book ${ref.chapter}"
                verses.start == verses.end -> "$book ${ref.chapter}:${verses.start}"
                else -> "$book ${ref.chapter}:${verses.start}–${verses.end}"
            }
        } else {
            "$book ${run.first().chapter}–${run.last().chapter}"
        }
    }

    /**
     * Resolves the displayed book name, applying the Psalms singular/plural rule (D-UI-2):
     * the full canonical "Psalms" becomes "Psalm" for a single-chapter run. Only the full
     * form ([Book.canonicalName]) carries the plural; [Book.displayAbbrev] ("Psa") is left
     * untouched, so the abbreviated form is unaffected.
     */
    private fun displayBookName(
        book: Book,
        bookName: (Book) -> String,
        singleChapter: Boolean,
    ): String {
        val name = bookName(book)
        return if (singleChapter && name == "Psalms") "Psalm" else name
    }
}

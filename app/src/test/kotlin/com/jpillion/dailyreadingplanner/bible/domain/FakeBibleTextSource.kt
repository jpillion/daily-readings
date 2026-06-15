package com.jpillion.dailyreadingplanner.bible.domain

import com.jpillion.dailyreadingplanner.bible.domain.model.VerseId
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseRange
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseText

/**
 * Test double for the seam. Returns synthetic verses for any range: a verse-0 title for Psalms
 * chapters, then verses 1..3 — enough to assert ordering, block grouping, and title passthrough
 * without the real asset (the asset itself is the Sprint-A gate's job).
 */
class FakeBibleTextSource : BibleTextSource {
    val rangesRequested = mutableListOf<VerseRange>()

    override suspend fun getVerses(range: VerseRange): List<VerseText> {
        rangesRequested.add(range)
        val book = VerseId.book(range.startVerseId)
        val chapter = VerseId.chapter(range.startVerseId)
        val out = mutableListOf<VerseText>()
        if (book == 19) { // Psalms — synthetic superscription at verse 0
            out.add(
                VerseText(
                    canonicalId = VerseId.encode(book, chapter, 0),
                    nativeLabel = "",
                    isTitle = true,
                    markup = "A Psalm of David.",
                ),
            )
        }
        for (v in 1..3) {
            out.add(
                VerseText(
                    canonicalId = VerseId.encode(book, chapter, v),
                    nativeLabel = v.toString(),
                    isTitle = false,
                    markup = "verse $v of $book:$chapter",
                ),
            )
        }
        return out
    }
}

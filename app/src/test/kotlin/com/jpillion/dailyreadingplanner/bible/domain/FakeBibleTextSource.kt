package com.jpillion.dailyreadingplanner.bible.domain

import com.jpillion.dailyreadingplanner.bible.domain.model.VerseId
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseRange
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseText

/**
 * Test double for the seam. Window-aware: it returns exactly the verses whose canonical id falls
 * inside the requested [VerseRange], so a verse-windowed range (the four Psalm-119 days) yields
 * ONLY the windowed verses — the JVM proof that the reader renders verses 1–40 and no others.
 *
 * Each (book, chapter) has [SYNTHETIC_CHAPTER_LENGTH] body verses (more than enough to contain the
 * Psalm-119 windows in tests), plus a verse-0 superscription for Psalms — emitted only when the
 * requested range actually includes verse 0 (so a body window like [1..40] never spuriously gains a
 * title). A whole-chapter range [(…,0)…(…,999)] yields title + verses 1..SYNTHETIC_CHAPTER_LENGTH,
 * preserving every existing ordering/block/title assertion.
 */
class FakeBibleTextSource : BibleTextSource {
    val rangesRequested = mutableListOf<VerseRange>()

    override suspend fun getVerses(range: VerseRange): List<VerseText> {
        rangesRequested.add(range)
        val book = VerseId.book(range.startVerseId)
        val chapter = VerseId.chapter(range.startVerseId)
        val startVerse = VerseId.verse(range.startVerseId)
        val endVerse = VerseId.verse(range.endVerseId)
        val out = mutableListOf<VerseText>()
        if (book == 19 && startVerse == 0) { // Psalms superscription only if the window includes v0
            out.add(
                VerseText(
                    canonicalId = VerseId.encode(book, chapter, 0),
                    nativeLabel = "",
                    isTitle = true,
                    markup = "A Psalm of David.",
                ),
            )
        }
        val firstBody = maxOf(1, startVerse)
        val lastBody = minOf(SYNTHETIC_CHAPTER_LENGTH, endVerse)
        for (v in firstBody..lastBody) {
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

    private companion object {
        const val SYNTHETIC_CHAPTER_LENGTH = 176
    }
}

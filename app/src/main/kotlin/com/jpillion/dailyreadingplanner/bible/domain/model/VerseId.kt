package com.jpillion.dailyreadingplanner.bible.domain.model

/**
 * VB-T1 / ESpec-v3 §5.1 — canonical verse_id encoding: `book*1_000_000 + chapter*1_000 + verse`.
 * Gen 1:1 = 1_001_001; John 3:16 = 43_003_016. `Long` (not `Int`) removes any doubt and matches
 * Room/SQLite integer affinity, though 66_022_021 fits in Int. The multipliers safely cover the
 * maxima (Psalms = 150 chapters, Ps 119 = 176 verses). Verse 0 (superscription) sorts before
 * verse 1 naturally, so [chapterRange] deliberately starts at verse 0 to include titles.
 */
object VerseId {
    fun encode(
        bookNo: Int,
        chapter: Int,
        verse: Int,
    ): Long = bookNo * 1_000_000L + chapter * 1_000L + verse

    fun book(id: Long): Int = (id / 1_000_000L).toInt()

    fun chapter(id: Long): Int = ((id / 1_000L) % 1_000L).toInt()

    fun verse(id: Long): Int = (id % 1_000L).toInt()

    /** Whole chapter incl. the verse-0 superscription. */
    fun chapterRange(
        bookNo: Int,
        chapter: Int,
    ): VerseRange = VerseRange(encode(bookNo, chapter, 0), encode(bookNo, chapter, 999))

    /** Whole book (every chapter, every verse, incl. superscriptions). */
    fun bookRange(bookNo: Int): VerseRange = VerseRange(encode(bookNo, 0, 0), encode(bookNo, 999, 999))
}

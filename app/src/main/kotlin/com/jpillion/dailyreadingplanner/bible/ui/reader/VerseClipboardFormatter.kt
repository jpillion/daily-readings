package com.jpillion.dailyreadingplanner.bible.ui.reader

import com.jpillion.dailyreadingplanner.bible.data.markup.MarkupStripper
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseId
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseText
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import com.jpillion.dailyreadingplanner.ui.day.ReadingFormatter

/**
 * Q1 / **D-Q-1** — the owner-chosen clipboard shape for copied verses: **text first, reference at
 * the end**.
 *
 * ```
 * In the beginning God created the heaven and the earth. And the earth was without form, and void…
 *
 * — Genesis 1:1–2 (KJV)
 * ```
 *
 * Pure and JVM-testable (no Android, no Compose); the sprint's primary mutation target. It backs
 * both copy paths — the multi-verse selection Copy action and the single-verse "Copy this verse"
 * menu item (a one-verse list produces exactly the same string).
 *
 * Rules, as specified:
 *  - Verses are sorted by `canonicalId` **inside** [format], so no caller can break the order.
 *  - Text is the stripped text ([MarkupStripper.strip]) — never raw `<a>` / `<w>` / `<l/>` markup —
 *    joined with a single space as running prose.
 *  - The reference groups by book (joined `"; "`), then by chapter within a book (joined `"; "`),
 *    collapsing contiguous verse runs to `a–b` (en dash, matching the app's existing reference
 *    rendering) with non-contiguous runs joined by `,`.
 *  - **The book name comes from [ReadingFormatter.singularizeBookName]** so D-UI-2 holds — a
 *    single Psalms chapter reads "Psalm 23:1", a multi-chapter selection "Psalms 23:6; 24:1". The
 *    singular/plural rule has ONE home and is never re-derived here.
 *  - **Superscriptions (verse 0)** contribute their text but no verse number; a chapter whose only
 *    selected verse is its superscription renders as the chapter alone ("Psalm 3").
 *  - The translation code comes from the `BibleTextSource.translations()` seam (Sprint 00N), not a
 *    hardcoded "KJV"; a null/blank code omits the parenthetical entirely.
 */
object VerseClipboardFormatter {
    /** En dash (U+2013) — the range separator used by every reference surface in the app. */
    private const val EN_DASH = '–'

    /**
     * Formats [verses] for the clipboard as `"<text>\n\n— <reference> (<CODE>)"`.
     *
     * @param verses the selected verses in any order; sorted by canonical id here.
     * @param translationCode e.g. "KJV"; null or blank omits the trailing parenthetical.
     * @return the clipboard string, or `""` when [verses] is empty (no dash, no reference).
     */
    fun format(
        verses: List<VerseText>,
        translationCode: String?,
    ): String {
        if (verses.isEmpty()) return ""
        val sorted = verses.sortedBy { it.canonicalId }
        val text =
            sorted
                .map { MarkupStripper.strip(it.markup) }
                .filter { it.isNotBlank() }
                .joinToString(" ")
        val code = if (translationCode.isNullOrBlank()) "" else " ($translationCode)"
        return "$text\n\n— ${reference(sorted)}$code"
    }

    /** Book groups, in canonical order, joined with "; " — e.g. "2 John 1:1; 3 John 1:1". */
    private fun reference(sorted: List<VerseText>): String =
        sorted
            .groupBy { VerseId.book(it.canonicalId) }
            .entries
            .joinToString("; ") { (bookNo, inBook) -> bookGroup(bookNo, inBook) }

    /**
     * One book's part of the reference: "<BookName> <chapter groups>". The name is resolved from
     * [BookCatalog] by canon order and then run through [ReadingFormatter.singularizeBookName] with
     * `singleChapter` = "this book group covers exactly one distinct chapter" (D-UI-2).
     */
    private fun bookGroup(
        bookNo: Int,
        inBook: List<VerseText>,
    ): String {
        val byChapter = inBook.groupBy { VerseId.chapter(it.canonicalId) }
        val canonicalName = BookCatalog.books.firstOrNull { it.order == bookNo }?.canonicalName ?: ""
        val bookName = ReadingFormatter.singularizeBookName(canonicalName, singleChapter = byChapter.size == 1)
        val chapters = byChapter.entries.joinToString("; ") { (chapter, inChapter) -> chapterGroup(chapter, inChapter) }
        return "$bookName $chapters"
    }

    /**
     * One chapter's part: "<ch>:<verse spec>", or just "<ch>" when nothing numbered is selected
     * (a superscription-only chapter — a Psalm title has no verse number).
     */
    private fun chapterGroup(
        chapter: Int,
        inChapter: List<VerseText>,
    ): String {
        val numbers = inChapter.map { VerseId.verse(it.canonicalId) }.filter { it > 0 }
        return if (numbers.isEmpty()) "$chapter" else "$chapter:${verseSpec(numbers)}"
    }

    /**
     * Ascending verse [numbers] with contiguous runs collapsed: `1,2,3,5` -> "1–3,5". Runs join
     * with a comma and no space, matching the D-Q-1 example "Genesis 1:1–2,5".
     */
    private fun verseSpec(numbers: List<Int>): String {
        val runs = mutableListOf<MutableList<Int>>()
        for (n in numbers) {
            val current = runs.lastOrNull()
            if (current != null && current.last() + 1 == n) current += n else runs += mutableListOf(n)
        }
        return runs.joinToString(",") { run ->
            if (run.size == 1) "${run.first()}" else "${run.first()}$EN_DASH${run.last()}"
        }
    }
}

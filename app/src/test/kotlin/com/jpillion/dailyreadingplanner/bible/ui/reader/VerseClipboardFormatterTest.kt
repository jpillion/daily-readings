package com.jpillion.dailyreadingplanner.bible.ui.reader

import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseId
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseText
import com.jpillion.dailyreadingplanner.ui.day.ReadingFormatter
import org.junit.Test

/**
 * Q1 / D-Q-1 — the clipboard shape pinned as LITERAL expected strings (never computed with the
 * production formatter): text, blank line, em dash, reference, translation code. Covers the single
 * verse, contiguous ranges, non-contiguous commas, cross-chapter grouping, the two-book portion,
 * the Psalms singular rule delegating to [ReadingFormatter.singularizeBookName] (D-UI-2),
 * superscriptions alone and with verses, and markup stripping.
 *
 * Verse texts are short fixtures — what is load-bearing here is the assembled string, not the
 * asset's wording (the KJV text itself is pinned by BibleTextVerificationTest).
 */
class VerseClipboardFormatterTest {
    private val genesis = 1
    private val psalms = 19
    private val secondJohn = 63
    private val thirdJohn = 64

    private fun verse(
        bookNo: Int,
        chapter: Int,
        verse: Int,
        markup: String,
        isTitle: Boolean = false,
    ): VerseText =
        VerseText(
            canonicalId = VerseId.encode(bookNo, chapter, verse),
            nativeLabel = if (isTitle) "" else verse.toString(),
            isTitle = isTitle,
            markup = markup,
        )

    private val gen11 = verse(genesis, 1, 1, "In the beginning God created the heaven and the earth.")
    private val gen12 = verse(genesis, 1, 2, "And the earth was without form, and void.")
    private val gen15 = verse(genesis, 1, 5, "And God called the light Day.")
    private val gen23 = verse(genesis, 2, 3, "And God blessed the seventh day.")
    private val ps231 = verse(psalms, 23, 1, "The <a>LORD</a> is my shepherd")
    private val ps232 = verse(psalms, 23, 2, "He <w>maketh</w> me<l/>to lie down.")
    private val ps236 = verse(psalms, 23, 6, "Surely goodness and mercy shall follow me.")
    private val ps241 = verse(psalms, 24, 1, "The earth is the LORD's.")
    private val ps3title = verse(psalms, 3, 0, "A Psalm of David, when he fled from Absalom.", isTitle = true)
    private val ps31 = verse(psalms, 3, 1, "LORD, how are they increased that trouble me!")
    private val secondJohn11 = verse(secondJohn, 1, 1, "The elder unto the elect lady.")
    private val thirdJohn11 = verse(thirdJohn, 1, 1, "The elder unto the wellbeloved Gaius.")

    @Test
    fun `a single verse is text then a blank line then the reference and code`() {
        val out = VerseClipboardFormatter.format(listOf(gen11), "KJV")

        assertThat(out).isEqualTo("In the beginning God created the heaven and the earth.\n\n— Genesis 1:1 (KJV)")
    }

    @Test
    fun `contiguous verses join as prose and collapse to an en dash range`() {
        val out = VerseClipboardFormatter.format(listOf(gen11, gen12), "KJV")

        val expected =
            "In the beginning God created the heaven and the earth. " +
                "And the earth was without form, and void." +
                "\n\n— Genesis 1:1–2 (KJV)"
        assertThat(out).isEqualTo(expected)
    }

    @Test
    fun `non-contiguous verses join runs with a comma and no space`() {
        val out = VerseClipboardFormatter.format(listOf(gen11, gen12, gen15), "KJV")

        val expected =
            "In the beginning God created the heaven and the earth. " +
                "And the earth was without form, and void. " +
                "And God called the light Day." +
                "\n\n— Genesis 1:1–2,5 (KJV)"
        assertThat(out).isEqualTo(expected)
    }

    @Test
    fun `a selection spanning chapters groups by chapter`() {
        val out = VerseClipboardFormatter.format(listOf(gen11, gen12, gen23), "KJV")

        val expected =
            "In the beginning God created the heaven and the earth. " +
                "And the earth was without form, and void. " +
                "And God blessed the seventh day." +
                "\n\n— Genesis 1:1–2; 2:3 (KJV)"
        assertThat(out).isEqualTo(expected)
    }

    @Test
    fun `a single Psalms chapter renders singular - delegated to singularizeBookName`() {
        val out = VerseClipboardFormatter.format(listOf(ps231), "KJV")

        // LITERAL pin (D-UI-2): breaking the delegation reddens this.
        assertThat(out).isEqualTo("The LORD is my shepherd\n\n— Psalm 23:1 (KJV)")
        // …and the literal agrees with the ONE home of the rule, so the two can never drift.
        assertThat(out).contains("— ${ReadingFormatter.singularizeBookName("Psalms", singleChapter = true)} 23:1")
    }

    @Test
    fun `a multi-chapter Psalms selection renders plural`() {
        val out = VerseClipboardFormatter.format(listOf(ps236, ps241), "KJV")

        val expected =
            "Surely goodness and mercy shall follow me. " +
                "The earth is the LORD's." +
                "\n\n— Psalms 23:6; 24:1 (KJV)"
        assertThat(out).isEqualTo(expected)
        assertThat(out).contains("— ${ReadingFormatter.singularizeBookName("Psalms", singleChapter = false)} 23:6")
    }

    @Test
    fun `a superscription alone references the chapter with no verse number`() {
        val out = VerseClipboardFormatter.format(listOf(ps3title), "KJV")

        assertThat(out).isEqualTo("A Psalm of David, when he fled from Absalom.\n\n— Psalm 3 (KJV)")
    }

    @Test
    fun `a superscription with a verse contributes text but no verse number`() {
        val out = VerseClipboardFormatter.format(listOf(ps3title, ps31), "KJV")

        val expected =
            "A Psalm of David, when he fled from Absalom. " +
                "LORD, how are they increased that trouble me!" +
                "\n\n— Psalm 3:1 (KJV)"
        assertThat(out).isEqualTo(expected)
    }

    @Test
    fun `a two-book selection joins the book groups`() {
        val out = VerseClipboardFormatter.format(listOf(secondJohn11, thirdJohn11), "KJV")

        val expected =
            "The elder unto the elect lady. " +
                "The elder unto the wellbeloved Gaius." +
                "\n\n— 2 John 1:1; 3 John 1:1 (KJV)"
        assertThat(out).isEqualTo(expected)
    }

    @Test
    fun `markup is stripped - never raw tags on the clipboard`() {
        val out = VerseClipboardFormatter.format(listOf(ps231, ps232), "KJV")

        assertThat(out).isEqualTo("The LORD is my shepherd He maketh me to lie down.\n\n— Psalm 23:1–2 (KJV)")
        assertThat(out).doesNotContain("<")
        assertThat(out).doesNotContain(">")
    }

    @Test
    fun `an empty selection formats to the empty string`() {
        assertThat(VerseClipboardFormatter.format(emptyList(), "KJV")).isEqualTo("")
        assertThat(VerseClipboardFormatter.format(emptyList(), null)).isEqualTo("")
    }

    @Test
    fun `a null translation code omits the parenthetical`() {
        val out = VerseClipboardFormatter.format(listOf(gen11), null)

        assertThat(out).isEqualTo("In the beginning God created the heaven and the earth.\n\n— Genesis 1:1")
    }

    @Test
    fun `a blank translation code omits the parenthetical`() {
        val out = VerseClipboardFormatter.format(listOf(gen11), "   ")

        assertThat(out).isEqualTo("In the beginning God created the heaven and the earth.\n\n— Genesis 1:1")
    }

    @Test
    fun `unsorted input is sorted inside format - callers cannot break the order`() {
        val out = VerseClipboardFormatter.format(listOf(gen15, gen11, gen12), "KJV")

        val expected =
            "In the beginning God created the heaven and the earth. " +
                "And the earth was without form, and void. " +
                "And God called the light Day." +
                "\n\n— Genesis 1:1–2,5 (KJV)"
        assertThat(out).isEqualTo(expected)
    }

    @Test
    fun `unsorted input across books and chapters still groups in canonical order`() {
        val out = VerseClipboardFormatter.format(listOf(thirdJohn11, ps241, secondJohn11, ps236), "KJV")

        val expected =
            "Surely goodness and mercy shall follow me. " +
                "The earth is the LORD's. " +
                "The elder unto the elect lady. " +
                "The elder unto the wellbeloved Gaius." +
                "\n\n— Psalms 23:6; 24:1; 2 John 1:1; 3 John 1:1 (KJV)"
        assertThat(out).isEqualTo(expected)
    }
}

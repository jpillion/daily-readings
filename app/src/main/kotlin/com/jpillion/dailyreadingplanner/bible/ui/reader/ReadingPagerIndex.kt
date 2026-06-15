package com.jpillion.dailyreadingplanner.bible.ui.reader

import com.jpillion.dailyreadingplanner.data.reference.Book
import com.jpillion.dailyreadingplanner.domain.model.Portion

/**
 * I1 (D-I-1) — the portion-anchored chapter index for the reader's **Reading** context (a Schedule
 * reading tap), the analogue of [GlobalChapterIndex] for the **Browse** context.
 *
 * A reading-plan [Portion] spans a contiguous run of global chapters `[first … last]` (every plan
 * portion is contiguous, INCLUDING the two-book Jun 19 / Dec 19 portion whose 2 John 1 and 3 John 1
 * are globally adjacent — the [contiguous]/[portionFirstGlobal]/[portionLastGlobal] computation
 * asserts this). In the Reading context that whole run COLLAPSES to ONE atomic page:
 *
 * ```
 *   global:   … g-2  g-1  [ first … last ]  last+1  last+2 …
 *   page:     … p-2  p-1  [    portion   ]   p+1     p+2  …
 * ```
 *
 * - Pages `0 .. first-1` map 1:1 to the single global chapters BEFORE the portion.
 * - Page [portionPage] (== `first`) is the portion — rendered as the whole multi-chapter portion.
 * - Pages `portionPage+1 ..` map to the single global chapters AFTER the portion (the run between
 *   `first` and `last` is gone), shifted left by [collapsedSpan] (`last - first`).
 *
 * So [pageCount] == [GlobalChapterIndex.TOTAL_CHAPTERS] - collapsedSpan, and the Genesis-1 /
 * Revelation-22 bounds hold for free (the pager cannot scroll past page 0 / pageCount-1). Swiping
 * right from the portion lands on the next single chapter after `last`; swiping back left returns
 * to the SAME atomic portion page (never the portion's last chapter alone) — the owner's
 * consistency guarantee, which falls out of the portion being one fixed page index.
 *
 * Pure / JVM-unit-testable; the [Book]/chapter at any page resolves through [GlobalChapterIndex],
 * never re-derived. A single-chapter portion (776 of the 1,095 readings) is just collapsedSpan == 0,
 * so this degrades to one-chapter-per-page exactly like Browse.
 */
class ReadingPagerIndex(
    portion: Portion,
) {
    /** The portion's first ref's global chapter index. */
    val portionFirstGlobal: Int =
        GlobalChapterIndex.indexOf(portion.refs.first().book, portion.refs.first().chapter)

    /** The portion's last ref's global chapter index. */
    val portionLastGlobal: Int =
        GlobalChapterIndex.indexOf(portion.refs.last().book, portion.refs.last().chapter)

    init {
        require(portionLastGlobal >= portionFirstGlobal) {
            "portion global span is reversed: $portionFirstGlobal..$portionLastGlobal"
        }
        // Contiguity: the portion's refs must be exactly the consecutive global chapters
        // first..last (no gaps, no out-of-order). Every real plan portion satisfies this; the
        // assertion documents and guards the invariant the collapse depends on.
        require(portion.refs.size == portionLastGlobal - portionFirstGlobal + 1) {
            "portion is not a contiguous global-chapter run: ${portion.refs.size} refs over " +
                "[$portionFirstGlobal..$portionLastGlobal]"
        }
        portion.refs.forEachIndexed { i, ref ->
            require(GlobalChapterIndex.indexOf(ref.book, ref.chapter) == portionFirstGlobal + i) {
                "portion refs are not consecutive global chapters at index $i"
            }
        }
    }

    /** The number of EXTRA chapters folded into the portion page beyond the first (0 for 1 chapter). */
    val collapsedSpan: Int = portionLastGlobal - portionFirstGlobal

    /** The fixed page index of the atomic portion page (== the portion's first global chapter index). */
    val portionPage: Int = portionFirstGlobal

    /** The Reading pager's page count: the canon minus the chapters folded into the portion page. */
    val pageCount: Int = GlobalChapterIndex.TOTAL_CHAPTERS - collapsedSpan

    /** True when [page] is the atomic portion page. */
    fun isPortionPage(page: Int): Boolean = page == portionPage

    /**
     * The single (book, chapter) shown at a NON-portion [page]. Throws on the portion page (a
     * programming error — callers must branch on [isPortionPage]) or an out-of-range page.
     */
    fun chapterAt(page: Int): Pair<Book, Int> {
        require(page in 0 until pageCount) { "reading page $page out of 0..${pageCount - 1}" }
        require(page != portionPage) { "page $page is the portion page; branch on isPortionPage" }
        // Pages before the portion are identity; pages after are shifted back over the collapsed run.
        val global = if (page < portionPage) page else page + collapsedSpan
        return GlobalChapterIndex.chapterAt(global)
    }
}

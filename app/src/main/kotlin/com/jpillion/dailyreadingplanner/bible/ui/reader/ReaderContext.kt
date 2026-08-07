package com.jpillion.dailyreadingplanner.bible.ui.reader

import com.jpillion.dailyreadingplanner.domain.model.Portion

/**
 * I2 (D-I-1) — which of the reader's two pager configurations is active.
 *
 * The two contexts are deliberately separate index spaces (see [GlobalChapterIndex] vs
 * [ReadingPagerIndex]); the route reads the active context to build the [androidx.compose.foundation.pager.HorizontalPager]
 * with the right page count and initial page, and rebuilds it on a switch.
 */
sealed interface ReaderContext {
    /** The Bible-tab / picker single-chapter swipe over the whole canon ([GlobalChapterIndex]). */
    data object Browse : ReaderContext

    /**
     * A Schedule reading tap: the [portion] is one atomic page in [index], flanked by the single
     * chapters before/after it (D-I-1).
     */
    data class Reading(
        val portion: Portion,
        val index: ReadingPagerIndex,
    ) : ReaderContext
}

/** The number of pager pages the active context exposes. */
val ReaderContext.pageCount: Int
    get() =
        when (this) {
            ReaderContext.Browse -> GlobalChapterIndex.TOTAL_CHAPTERS
            is ReaderContext.Reading -> index.pageCount
        }

/**
 * What the reader's `HorizontalPager` must be built as: which [page] to open on, and an [epoch] that
 * changes whenever the pager has to be rebuilt (a Browse <-> Reading switch, or a picker jump).
 *
 * The epoch exists because the page alone is not a sufficient key. Jumping back to the page the
 * pager was originally seeded with leaves the key unchanged, so the pager would stay wherever the
 * user had swiped instead of returning to the picked chapter.
 */
data class PagerTarget(
    val epoch: Int,
    val page: Int,
)

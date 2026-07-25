package com.jpillion.dailyreadingplanner.bible.ui.reader

/**
 * Q1 — the reader's multi-select state, pure and JVM-testable (the [GlobalChapterIndex] precedent:
 * a plain object in `bible/ui/reader`, no Compose, no Android, no ViewModel).
 *
 * Selection is entered by a long-press on a verse (or the equivalent "Select verses" menu item, the
 * TalkBack-reachable path) and is exited by the X affordance, system back, or deselecting the last
 * verse. While a selection is active a short tap toggles verses instead of opening the verse menu.
 *
 * **D-Q-3 — selection scope is the current page.** A page is one chapter (Browse) or one whole
 * portion (Reading, incl. the multi-chapter and the two-book Jun 19 / Dec 19 portions), so
 * multi-chapter selection works naturally inside a portion. Swiping to another page clears the
 * selection ([onPageChanged]) rather than leaving an invisible off-screen selection alive.
 * Cross-page selection is deliberately out of scope, which is why [page] is part of the state.
 *
 * The empty selection is always exactly [NONE] (`page == null`), so "no selection" has one
 * representation and `state == VerseSelection.NONE` is a safe inactivity test.
 *
 * @property page the reader pager page the selection belongs to; null iff nothing is selected.
 * @property verseIds the selected canonical verse ids ([com.jpillion.dailyreadingplanner.bible.domain.model.VerseId]).
 */
data class VerseSelection(
    val page: Int? = null,
    val verseIds: Set<Long> = emptySet(),
) {
    /** True while selection mode is on, i.e. at least one verse is selected. */
    val isActive: Boolean get() = verseIds.isNotEmpty()

    /** The number of selected verses — what the contextual action bar announces. */
    val count: Int get() = verseIds.size

    /** Whether [verseId] is currently selected (drives the selected semantics on the verse row). */
    fun contains(verseId: Long): Boolean = verseId in verseIds

    companion object {
        /** The one representation of "nothing selected": no page, no verses. */
        val NONE = VerseSelection()

        /**
         * Enters selection mode on [page] with exactly [verseId] selected — the long-press and the
         * "Select verses" menu item both land here.
         */
        fun start(
            page: Int,
            verseId: Long,
        ): VerseSelection = VerseSelection(page, setOf(verseId))
    }
}

/**
 * A short tap on [verseId] while selecting: adds it if absent, removes it if present.
 *
 * Deselecting the LAST verse exits selection mode entirely — the result is [VerseSelection.NONE],
 * so the page is cleared to null too, never a "page with an empty set".
 *
 * The two defensive branches cannot be reached in production (a tap outside selection mode opens
 * the verse menu, and a page change clears the selection first via [onPageChanged]), but both
 * resolve to the only sane answer — start a fresh selection on the tapped page.
 */
fun VerseSelection.toggle(
    page: Int,
    verseId: Long,
): VerseSelection =
    when {
        !isActive -> VerseSelection.start(page, verseId)
        page != this.page -> VerseSelection.start(page, verseId)
        contains(verseId) -> {
            val remaining = verseIds - verseId
            if (remaining.isEmpty()) VerseSelection.NONE else copy(verseIds = remaining)
        }
        else -> copy(verseIds = verseIds + verseId)
    }

/**
 * D-Q-3 — the reader settled on pager [page]. A selection survives only on the page it was made on;
 * any other page clears it to [VerseSelection.NONE]. Idempotent: an inactive selection stays
 * [VerseSelection.NONE] whatever page is reported.
 */
fun VerseSelection.onPageChanged(page: Int): VerseSelection = if (this.page == page) this else VerseSelection.NONE

/** Explicit exit — the contextual action bar's X affordance and system back. Always [VerseSelection.NONE]. */
fun VerseSelection.cleared(): VerseSelection = VerseSelection.NONE

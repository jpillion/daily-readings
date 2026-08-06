package com.jpillion.dailyreadingplanner.bible.ui.reader

import com.jpillion.dailyreadingplanner.bible.domain.model.BibleVersion
import com.jpillion.dailyreadingplanner.bible.domain.model.ChapterContent

/**
 * VC-T1 — everything the reader screen can show. Mirrors the app's sealed-UI-state idiom
 * (see ui/day/DayUiState.kt): a stateless screen renders one of these, the route/ViewModel owns
 * the transitions.
 */
sealed interface ReaderUiState {
    data object Loading : ReaderUiState

    /**
     * Rendered-ready content. [blocks] render top-to-bottom — one block per chapter (a single
     * browsed chapter is one block; an opened reading-plan portion is its [com.jpillion.dailyreadingplanner.bible.domain.model.PortionContent.blocks]).
     * [title] is a short top-bar header. [activeVerseId] is the reserved audio seam (D-V3-14):
     * ALWAYS null in V3.0; the verse item reads it for a (future V4) highlight background.
     */
    data class Content(
        val blocks: List<ChapterContent>,
        val title: String,
        // D-V3-14: reserved audio seam, always null in V3.0.
        val activeVerseId: Long? = null,
        /**
         * D-OT-2 case 3 — the online version this page asked for and could not get, so bundled KJV
         * is on screen instead; null when nothing degraded. Carries the *version* rather than a bare
         * flag so the banner can name it ("Unable to download NKJV, displaying KJV") — the owner's
         * requirement that the notice explain WHY KJV is showing.
         *
         * Held on the page's own state, never as shared ViewModel state: with a pager rendering
         * several pages a single flag would race and banner the wrong page.
         */
        val degradedFrom: BibleVersion? = null,
    ) : ReaderUiState {
        val degraded: Boolean get() = degradedFrom != null
    }

    data class Error(
        val canRetry: Boolean = true,
    ) : ReaderUiState
}

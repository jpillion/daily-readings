package com.jpillion.dailyreadingplanner.ui.navigation

import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.Reference
import com.jpillion.dailyreadingplanner.domain.model.Stream
import org.junit.Test

/**
 * I4 (D-I-2) — the handoff seam now carries two mutually-exclusive signals: a reading-tap portion
 * (→ Reading) and a Bible-tab browse reset (→ Browse). Pin the precedence (a reading tap wins) and
 * that both reads are single-shot.
 */
class ReaderHandoffTest {
    private fun portion() =
        Portion(
            Stream.NEW_TESTAMENT,
            listOf(Reference(BookCatalog.requireByName("James"), 4)),
        )

    @Test
    fun `consume returns and clears the pending portion (single-shot)`() {
        val handoff = ReaderHandoff()
        handoff.request(portion())
        assertThat(handoff.consume()).isNotNull()
        assertThat(handoff.consume()).isNull()
    }

    @Test
    fun `requestBrowse raises a single-shot browse request`() {
        val handoff = ReaderHandoff()
        handoff.requestBrowse()
        assertThat(handoff.browseRequested.value).isTrue()
        assertThat(handoff.consumeBrowseRequest()).isTrue()
        assertThat(handoff.browseRequested.value).isFalse()
        assertThat(handoff.consumeBrowseRequest()).isFalse()
    }

    @Test
    fun `a reading request supersedes a stale browse request`() {
        val handoff = ReaderHandoff()
        handoff.requestBrowse()
        handoff.request(portion())
        // The portion is now pending and the browse request was cleared (reading wins).
        assertThat(handoff.pending.value).isNotNull()
        assertThat(handoff.browseRequested.value).isFalse()
    }

    @Test
    fun `requestBrowse does not override a pending reading open`() {
        val handoff = ReaderHandoff()
        handoff.request(portion())
        handoff.requestBrowse()
        // A pending reading open is protected from a bare tab tap.
        assertThat(handoff.pending.value).isNotNull()
        assertThat(handoff.browseRequested.value).isFalse()
    }
}

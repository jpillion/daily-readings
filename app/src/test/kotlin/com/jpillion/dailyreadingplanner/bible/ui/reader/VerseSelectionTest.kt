package com.jpillion.dailyreadingplanner.bible.ui.reader

import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseId
import org.junit.Test

/**
 * Q1 — the selection reducer pinned pure (no Robolectric): long-press enters with exactly one
 * verse, a tap toggles, deselecting the last exits to [VerseSelection.NONE], and a page change
 * clears (D-Q-3, selection scope is the current page).
 */
class VerseSelectionTest {
    private val gen11 = VerseId.encode(1, 1, 1)
    private val gen12 = VerseId.encode(1, 1, 2)
    private val gen13 = VerseId.encode(1, 1, 3)

    @Test
    fun `NONE is inactive and empty`() {
        assertThat(VerseSelection.NONE.isActive).isFalse()
        assertThat(VerseSelection.NONE.count).isEqualTo(0)
        assertThat(VerseSelection.NONE.page).isNull()
        assertThat(VerseSelection.NONE.verseIds).isEmpty()
        assertThat(VerseSelection.NONE.contains(gen11)).isFalse()
    }

    @Test
    fun `long press enters selection mode with exactly that verse`() {
        val selection = VerseSelection.start(page = 5, verseId = gen11)

        assertThat(selection.isActive).isTrue()
        assertThat(selection.page).isEqualTo(5)
        assertThat(selection.count).isEqualTo(1)
        assertThat(selection.verseIds).containsExactly(gen11)
        assertThat(selection.contains(gen11)).isTrue()
        assertThat(selection.contains(gen12)).isFalse()
    }

    @Test
    fun `tap toggles a second verse on and count tracks`() {
        val selection = VerseSelection.start(page = 5, verseId = gen11).toggle(page = 5, verseId = gen12)

        assertThat(selection.page).isEqualTo(5)
        assertThat(selection.verseIds).containsExactly(gen11, gen12)
        assertThat(selection.count).isEqualTo(2)

        val third = selection.toggle(page = 5, verseId = gen13)
        assertThat(third.count).isEqualTo(3)
        assertThat(third.verseIds).containsExactly(gen11, gen12, gen13)
    }

    @Test
    fun `tapping an already selected verse removes it`() {
        val selection =
            VerseSelection
                .start(page = 5, verseId = gen11)
                .toggle(page = 5, verseId = gen12)
                .toggle(page = 5, verseId = gen11)

        assertThat(selection.isActive).isTrue()
        assertThat(selection.page).isEqualTo(5)
        assertThat(selection.verseIds).containsExactly(gen12)
        assertThat(selection.count).isEqualTo(1)
    }

    @Test
    fun `deselecting the last verse exits selection mode entirely`() {
        val selection = VerseSelection.start(page = 5, verseId = gen11).toggle(page = 5, verseId = gen11)

        assertThat(selection).isEqualTo(VerseSelection.NONE)
        assertThat(selection.page).isNull()
        assertThat(selection.isActive).isFalse()
        assertThat(selection.count).isEqualTo(0)
    }

    @Test
    fun `toggle on an inactive selection starts one - defensive`() {
        val selection = VerseSelection.NONE.toggle(page = 7, verseId = gen12)

        assertThat(selection).isEqualTo(VerseSelection.start(page = 7, verseId = gen12))
    }

    @Test
    fun `toggle on a different page restarts the selection there - defensive`() {
        val selection = VerseSelection.start(page = 5, verseId = gen11).toggle(page = 6, verseId = gen12)

        assertThat(selection.page).isEqualTo(6)
        assertThat(selection.verseIds).containsExactly(gen12)
    }

    @Test
    fun `a page change to another page clears the selection`() {
        val selection = VerseSelection.start(page = 5, verseId = gen11).toggle(page = 5, verseId = gen12)

        assertThat(selection.onPageChanged(6)).isEqualTo(VerseSelection.NONE)
        assertThat(selection.onPageChanged(6).page).isNull()
        assertThat(selection.onPageChanged(6).isActive).isFalse()
    }

    @Test
    fun `a page change to the same page leaves the selection untouched`() {
        val selection = VerseSelection.start(page = 5, verseId = gen11).toggle(page = 5, verseId = gen12)

        assertThat(selection.onPageChanged(5)).isSameInstanceAs(selection)
    }

    @Test
    fun `a page change from an inactive selection stays NONE`() {
        assertThat(VerseSelection.NONE.onPageChanged(0)).isEqualTo(VerseSelection.NONE)
        assertThat(VerseSelection.NONE.onPageChanged(42)).isEqualTo(VerseSelection.NONE)
    }

    @Test
    fun `cleared always returns NONE`() {
        val selection = VerseSelection.start(page = 5, verseId = gen11).toggle(page = 5, verseId = gen12)

        assertThat(selection.cleared()).isEqualTo(VerseSelection.NONE)
        assertThat(VerseSelection.NONE.cleared()).isEqualTo(VerseSelection.NONE)
    }
}

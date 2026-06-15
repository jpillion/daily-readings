package com.jpillion.dailyreadingplanner.ui.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * I4 (D-I-2) — the root glue: tapping the Bible tab raises the reader's browse-reset on the shared
 * handoff. (The reset's effect on the reader is pinned in ReaderViewModelTest.)
 */
class RootViewModelTest {
    @Test
    fun `onBibleTabSelected raises a browse request on the handoff`() {
        val handoff = ReaderHandoff()
        val vm = RootViewModel(handoff)
        vm.onBibleTabSelected()
        assertThat(handoff.browseRequested.value).isTrue()
    }
}

package com.jpillion.dailyreadingplanner.bible.ui.reader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Q2 / **D-Q-5** — the copy-confirmation rule, pinned at its boundary.
 *
 * Android 13 (API 33) draws its own system clipboard confirmation on every copy, so the app must
 * NOT add a toast there (it would double-report the same event); below 33 nothing is shown by the
 * system, so the app has to confirm itself. The rule takes the SDK level as a parameter precisely
 * so it is provable here — no device, no Robolectric SDK swap.
 *
 * The clipboard write itself ([copyVerseTextToClipboard]) is a thin platform call and is a
 * device-pass item (the pasted result landing correctly in a notes/messages app).
 */
class VerseClipboardTest {
    @Test
    fun `below API 33 the app shows its own Copied confirmation`() {
        assertThat(shouldShowCopyConfirmation(26)).isTrue() // minSdk
        assertThat(shouldShowCopyConfirmation(31)).isTrue()
        assertThat(shouldShowCopyConfirmation(32)).isTrue() // the boundary, one below
    }

    @Test
    fun `from API 33 the system confirmation is the only one (no app toast)`() {
        assertThat(shouldShowCopyConfirmation(33)).isFalse() // the boundary
        assertThat(shouldShowCopyConfirmation(34)).isFalse()
        assertThat(shouldShowCopyConfirmation(40)).isFalse() // future levels stay silent
    }
}

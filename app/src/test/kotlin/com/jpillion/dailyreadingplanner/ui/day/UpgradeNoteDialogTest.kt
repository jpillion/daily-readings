package com.jpillion.dailyreadingplanner.ui.day

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.ui.theme.DailyReadingPlannerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * VD-T10 (OQ-2): the one-time upgrade note — "use it now" opts into the in-app reader; "keep my
 * current choice" preserves the external provider. The boolean reported distinguishes the two.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpgradeNoteDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val dismissals = mutableListOf<Boolean>()

    private fun setDialog() {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                UpgradeNoteDialog(onDismiss = { dismissals += it })
            }
        }
    }

    @Test
    fun `the note shows both actions`() {
        setDialog()
        composeRule.onNodeWithTag("upgrade-note").assertIsDisplayed()
        composeRule.onNodeWithTag("upgrade-note-use-it-now").assertIsDisplayed()
        composeRule.onNodeWithTag("upgrade-note-dismiss").assertIsDisplayed()
    }

    @Test
    fun `use it now reports a switch to in-app`() {
        setDialog()
        composeRule.onNodeWithTag("upgrade-note-use-it-now").performClick()
        assertThat(dismissals).containsExactly(true)
    }

    @Test
    fun `keep my current choice preserves the provider`() {
        setDialog()
        composeRule.onNodeWithTag("upgrade-note-dismiss").performClick()
        assertThat(dismissals).containsExactly(false)
    }
}

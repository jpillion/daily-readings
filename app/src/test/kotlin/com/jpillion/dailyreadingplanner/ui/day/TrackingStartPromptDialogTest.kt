package com.jpillion.dailyreadingplanner.ui.day

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.ui.theme.DailyReadingPlannerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * S19, D-S19-1: the one-time first-run tracking-start prompt — exactly three choices, plain
 * copy (LITERAL pin for owner tone sign-off), the custom path through the shared S10
 * full-calendar picker, and the rule that canceling the picker is NOT an answer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackingStartPromptDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 6, 10)
    private val chosen = mutableListOf<LocalDate>()
    private var dismissCalls = 0

    private fun setDialog() {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                TrackingStartPromptDialog(
                    today = today,
                    onChoose = { chosen += it },
                    onDismiss = { dismissCalls++ },
                )
            }
        }
    }

    @Test
    fun `prompt offers exactly the three choices with the explainer copy - literal pin`() {
        setDialog()
        composeRule.onNodeWithTag("tracking-start-prompt").assertIsDisplayed()
        composeRule.onNodeWithText("When should tracking start?").assertIsDisplayed()
        // Tone sign-off pin (PRD M8): one sentence of meaning + the Settings reassurance.
        composeRule
            .onNodeWithText(
                "Days before your start date aren’t counted as missed. " +
                    "You can change this anytime in Settings.",
            ).assertIsDisplayed()
        composeRule.onNodeWithText("Start from January 1, 2026").assertIsDisplayed()
        composeRule.onNodeWithText("Start from today (Jun 10, 2026)").assertIsDisplayed()
        composeRule.onNodeWithText("Pick a date…").assertIsDisplayed()
    }

    @Test
    fun `January 1 choice returns Jan 1 of the current year`() {
        setDialog()
        composeRule.onNodeWithTag("tracking-prompt-jan1").performClick()
        assertThat(chosen).containsExactly(LocalDate.of(2026, 1, 1))
        assertThat(dismissCalls).isEqualTo(0)
    }

    @Test
    fun `today choice returns today`() {
        setDialog()
        composeRule.onNodeWithTag("tracking-prompt-today").performClick()
        assertThat(chosen).containsExactly(today)
    }

    @Test
    fun `pick a date opens the shared full-calendar picker and confirm returns its date`() {
        setDialog()
        composeRule.onNodeWithTag("tracking-prompt-custom").performClick()
        composeRule.onNodeWithTag("tracking-start-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("tracking-start-prompt").assertDoesNotExist()
        // The picker is pre-selected on today; confirming chooses it.
        composeRule.onNodeWithTag("tracking-start-confirm").performClick()
        assertThat(chosen).containsExactly(today)
    }

    @Test
    fun `canceling the picker is not an answer - it returns to the prompt`() {
        setDialog()
        composeRule.onNodeWithTag("tracking-prompt-custom").performClick()
        composeRule.onNodeWithTag("tracking-start-cancel").performClick()
        composeRule.onNodeWithTag("tracking-start-prompt").assertIsDisplayed()
        assertThat(chosen).isEmpty()
        assertThat(dismissCalls).isEqualTo(0)
    }
}

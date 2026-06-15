package com.jpillion.dailyreadingplanner.ui.day

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestinationMode
import com.jpillion.dailyreadingplanner.ui.theme.DailyReadingPlannerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * VD-T7 (D-V3-19): the first-run reading-destination question — two equal, explicit choices, with
 * in-app NEVER silently pre-selected. Each tap reports the matching destination mode.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadingDestinationPromptDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val chosen = mutableListOf<ReadingDestinationMode>()

    private fun setDialog() {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                ReadingDestinationPromptDialog(onChoose = { chosen += it }, onDismiss = {})
            }
        }
    }

    @Test
    fun `the question shows both reading destinations`() {
        setDialog()
        composeRule.onNodeWithTag("reading-destination-prompt").assertIsDisplayed()
        composeRule.onNodeWithTag("reading-destination-prompt-inapp").assertIsDisplayed()
        composeRule.onNodeWithTag("reading-destination-prompt-external").assertIsDisplayed()
    }

    @Test
    fun `choosing in-app reports the in-app mode`() {
        setDialog()
        composeRule.onNodeWithTag("reading-destination-prompt-inapp").performClick()
        assertThat(chosen).containsExactly(ReadingDestinationMode.IN_APP)
    }

    @Test
    fun `choosing external reports the external mode`() {
        setDialog()
        composeRule.onNodeWithTag("reading-destination-prompt-external").performClick()
        assertThat(chosen).containsExactly(ReadingDestinationMode.EXTERNAL)
    }
}

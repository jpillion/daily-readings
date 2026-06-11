package com.jpillion.dailyreadingplanner.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.domain.model.ThemeMode
import com.jpillion.dailyreadingplanner.ui.theme.DailyReadingPlannerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings screen behavior (FR-9): selector state, selection callbacks, back navigation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val selections = mutableListOf<ThemeMode>()
    private var backCalls = 0

    private fun setScreen(selectedMode: ThemeMode) {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                SettingsScreen(
                    selectedMode = selectedMode,
                    onThemeModeSelected = { selections += it },
                    onBack = { backCalls++ },
                )
            }
        }
    }

    @Test
    fun rendersThemeSection_withTheSelectedModeChecked() {
        setScreen(ThemeMode.DARK)
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Theme").assertIsDisplayed()
        composeRule.onNodeWithTag("theme-option-light").assertIsDisplayed().assertIsNotSelected()
        composeRule.onNodeWithTag("theme-option-dark").assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithTag("theme-option-system").assertIsDisplayed().assertIsNotSelected()
    }

    @Test
    fun systemDefault_isTheSelectedRowForSystemMode() {
        setScreen(ThemeMode.SYSTEM)
        composeRule.onNodeWithTag("theme-option-system").assertIsSelected()
        composeRule.onNodeWithTag("theme-option-light").assertIsNotSelected()
        composeRule.onNodeWithTag("theme-option-dark").assertIsNotSelected()
    }

    @Test
    fun clickingARow_reportsThatMode() {
        setScreen(ThemeMode.SYSTEM)
        composeRule.onNodeWithTag("theme-option-dark").performClick()
        composeRule.onNodeWithTag("theme-option-light").performClick()
        assertThat(selections).containsExactly(ThemeMode.DARK, ThemeMode.LIGHT).inOrder()
    }

    @Test
    fun backButton_invokesOnBack() {
        setScreen(ThemeMode.SYSTEM)
        composeRule.onNodeWithTag("settings-back").performClick()
        assertThat(backCalls).isEqualTo(1)
    }
}

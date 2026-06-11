package com.jpillion.dailyreadingplanner.ui.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.domain.model.ThemeMode
import com.jpillion.dailyreadingplanner.ui.theme.DailyReadingPlannerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Settings screen behavior (FR-9 + S8): theme selector, the text-size slider, the
 * confirm-gated year-scoped reset, and back navigation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val selections = mutableListOf<ThemeMode>()
    private val fontScaleChanges = mutableListOf<Float>()
    private val trackingStartChanges = mutableListOf<LocalDate?>()
    private var resetConfirms = 0
    private var backCalls = 0

    private fun setScreen(
        selectedMode: ThemeMode,
        fontScale: Float = 1f,
        trackingStartDate: LocalDate? = null,
    ) {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                SettingsScreen(
                    selectedMode = selectedMode,
                    fontScale = fontScale,
                    currentYear = 2026,
                    trackingStartDate = trackingStartDate,
                    onThemeModeSelected = { selections += it },
                    onFontScaleChanged = { fontScaleChanges += it },
                    onTrackingStartChanged = { trackingStartChanges += it },
                    onResetProgressConfirmed = { resetConfirms++ },
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

    @Test
    fun textSizeSlider_showsTheCurrentScale_andReportsChanges() {
        setScreen(ThemeMode.SYSTEM, fontScale = 1.25f)
        composeRule.onNodeWithTag("text-size-value").assertTextEquals("125%")
        composeRule.onNodeWithTag("text-size-preview").assertIsDisplayed()
        composeRule
            .onNodeWithTag("text-size-slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(1.5f) }
        assertThat(fontScaleChanges).containsExactly(1.5f)
    }

    @Test
    fun resetProgress_requiresConfirmation_cancelDoesNothing() {
        setScreen(ThemeMode.SYSTEM)
        composeRule.onNodeWithTag("reset-progress").performScrollTo().performClick()
        composeRule.onNodeWithText("Reset this year's progress?").assertIsDisplayed()
        composeRule.onNodeWithTag("reset-cancel").performClick()
        composeRule.onNodeWithTag("reset-confirm").assertDoesNotExist()
        assertThat(resetConfirms).isEqualTo(0)
    }

    @Test
    fun resetProgress_confirm_firesExactlyOnce_namingTheYear() {
        setScreen(ThemeMode.SYSTEM)
        composeRule.onNodeWithTag("reset-progress").performScrollTo().performClick()
        // The dialog names the year being cleared (owner decision: current year only).
        composeRule
            .onNodeWithText("Every reading mark for 2026 will be cleared.", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("reset-confirm").performClick()
        composeRule.onNodeWithTag("reset-confirm").assertDoesNotExist()
        assertThat(resetConfirms).isEqualTo(1)
    }

    // --- S10: tracking start date row + full-date picker dialog. ---

    @Test
    fun trackingStartRow_showsNotSet_whenNull_andHidesClear() {
        setScreen(ThemeMode.SYSTEM)
        composeRule.onNodeWithTag("tracking-start-row").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("tracking-start-value", useUnmergedTree = true).assertTextEquals("Not set")
        composeRule.onNodeWithTag("tracking-start-clear").assertDoesNotExist()
    }

    @Test
    fun trackingStartRow_showsTheFormattedDate_whenSet() {
        setScreen(ThemeMode.SYSTEM, trackingStartDate = LocalDate.of(2026, 6, 3))
        composeRule
            .onNodeWithTag("tracking-start-value", useUnmergedTree = true)
            .performScrollTo()
            .assertTextEquals("Jun 3, 2026")
    }

    @Test
    fun trackingStartRow_opensThePicker_andConfirmReportsTheSelectedDate() {
        setScreen(ThemeMode.SYSTEM, trackingStartDate = LocalDate.of(2026, 6, 3))
        composeRule.onNodeWithTag("tracking-start-row").performScrollTo().performClick()
        composeRule.onNodeWithTag("tracking-start-dialog").assertIsDisplayed()
        // Confirm without changing the selection: reports the initially-selected date.
        composeRule.onNodeWithTag("tracking-start-confirm").performClick()
        composeRule.onNodeWithTag("tracking-start-dialog").assertDoesNotExist()
        assertThat(trackingStartChanges).containsExactly(LocalDate.of(2026, 6, 3))
    }

    @Test
    fun trackingStartPicker_cancelReportsNothing() {
        setScreen(ThemeMode.SYSTEM)
        composeRule.onNodeWithTag("tracking-start-row").performScrollTo().performClick()
        composeRule.onNodeWithTag("tracking-start-cancel").performClick()
        composeRule.onNodeWithTag("tracking-start-dialog").assertDoesNotExist()
        assertThat(trackingStartChanges).isEmpty()
    }

    @Test
    fun trackingStartClear_reportsNull_withoutOpeningTheDialog() {
        setScreen(ThemeMode.SYSTEM, trackingStartDate = LocalDate.of(2026, 6, 3))
        composeRule.onNodeWithTag("tracking-start-clear").performScrollTo().performClick()
        assertThat(trackingStartChanges).containsExactly(null as LocalDate?)
        composeRule.onNodeWithTag("tracking-start-dialog").assertDoesNotExist()
    }
}

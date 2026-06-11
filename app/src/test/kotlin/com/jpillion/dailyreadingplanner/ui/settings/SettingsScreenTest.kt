package com.jpillion.dailyreadingplanner.ui.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.domain.model.BibleProvider
import com.jpillion.dailyreadingplanner.domain.model.ThemeMode
import com.jpillion.dailyreadingplanner.ui.theme.DailyReadingPlannerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Settings screen behavior (FR-9 + S8; S14 reworked the theme + provider selectors into
 * compact dropdown rows): selectors, the text-size slider, the confirm-gated year-scoped
 * reset, and back navigation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val selections = mutableListOf<ThemeMode>()
    private val providerSelections = mutableListOf<BibleProvider>()
    private var requestAppCalls = 0
    private val fontScaleChanges = mutableListOf<Float>()
    private val trackingStartChanges = mutableListOf<LocalDate?>()
    private val reminderToggles = mutableListOf<Boolean>()
    private val reminderTimeChanges = mutableListOf<LocalTime>()
    private var rationaleDismissals = 0
    private var openNotificationSettingsCalls = 0
    private var resetConfirms = 0
    private var backCalls = 0

    private fun setScreen(
        selectedMode: ThemeMode,
        selectedProvider: BibleProvider = BibleProvider.BLB,
        fontScale: Float = 1f,
        trackingStartDate: LocalDate? = null,
        reminderEnabled: Boolean = false,
        reminderTime: LocalTime = LocalTime.of(8, 0),
        showReminderPermissionRationale: Boolean = false,
    ) {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                SettingsScreen(
                    selectedMode = selectedMode,
                    selectedProvider = selectedProvider,
                    fontScale = fontScale,
                    currentYear = 2026,
                    trackingStartDate = trackingStartDate,
                    reminderEnabled = reminderEnabled,
                    reminderTime = reminderTime,
                    showReminderPermissionRationale = showReminderPermissionRationale,
                    onThemeModeSelected = { selections += it },
                    onBibleProviderSelected = { providerSelections += it },
                    onRequestApp = { requestAppCalls++ },
                    onFontScaleChanged = { fontScaleChanges += it },
                    onTrackingStartChanged = { trackingStartChanges += it },
                    onReminderToggled = { reminderToggles += it },
                    onReminderTimeChanged = { reminderTimeChanges += it },
                    onPermissionRationaleDismissed = { rationaleDismissals++ },
                    onOpenNotificationSettings = { openNotificationSettingsCalls++ },
                    onResetProgressConfirmed = { resetConfirms++ },
                    onBack = { backCalls++ },
                )
            }
        }
    }

    @Test
    fun themeDropdownRow_showsTheCurrentMode_andSpeaksIt() {
        setScreen(ThemeMode.DARK)
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Theme").assertIsDisplayed()
        composeRule
            .onNodeWithTag("theme-dropdown")
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Theme, Dark")
        // The menu is closed until the row is tapped.
        composeRule.onNodeWithTag("theme-option-dark").assertDoesNotExist()
    }

    @Test
    fun openingTheThemeMenu_marksTheCurrentModeSelected() {
        setScreen(ThemeMode.SYSTEM)
        composeRule.onNodeWithTag("theme-dropdown").performClick()
        composeRule.onNodeWithTag("theme-option-system").assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithTag("theme-option-light").assertIsNotSelected()
        composeRule.onNodeWithTag("theme-option-dark").assertIsNotSelected()
    }

    @Test
    fun pickingAThemeFromTheMenu_reportsThatMode_andClosesTheMenu() {
        setScreen(ThemeMode.SYSTEM)
        composeRule.onNodeWithTag("theme-dropdown").performClick()
        composeRule.onNodeWithTag("theme-option-dark").performClick()
        composeRule.onNodeWithTag("theme-option-dark").assertDoesNotExist() // menu closed
        composeRule.onNodeWithTag("theme-dropdown").performClick() // reopen for a second pick
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

    // --- S12: reminders section (R-REM-1/2/7). ---

    @Test
    fun reminderToggle_isOffByDefault_andHidesTheTimeRow() {
        setScreen(ThemeMode.SYSTEM)
        composeRule
            .onNodeWithTag("reminder-toggle")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsOff()
        composeRule.onNodeWithTag("reminder-time-row").assertDoesNotExist()
    }

    @Test
    fun reminderToggle_reportsTheNewValue() {
        setScreen(ThemeMode.SYSTEM)
        composeRule.onNodeWithTag("reminder-toggle").performScrollTo().performClick()
        assertThat(reminderToggles).containsExactly(true)
    }

    @Test
    fun enabledReminder_showsTheTimeRow_withTheFormattedTime() {
        setScreen(ThemeMode.SYSTEM, reminderEnabled = true, reminderTime = LocalTime.of(21, 30))
        composeRule.onNodeWithTag("reminder-toggle").performScrollTo().assertIsOn()
        val expected = LocalTime.of(21, 30).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        composeRule
            .onNodeWithTag("reminder-time-value", useUnmergedTree = true)
            .performScrollTo()
            .assertTextEquals(expected)
    }

    @Test
    fun timeRow_opensThePicker_andConfirmReportsTheChosenTime() {
        setScreen(ThemeMode.SYSTEM, reminderEnabled = true, reminderTime = LocalTime.of(8, 0))
        composeRule.onNodeWithTag("reminder-time-row").performScrollTo().performClick()
        composeRule.onNodeWithTag("reminder-time-dialog").assertIsDisplayed()
        // Confirm without changing the dial: reports the initial time.
        composeRule.onNodeWithTag("reminder-time-confirm").performClick()
        composeRule.onNodeWithTag("reminder-time-dialog").assertDoesNotExist()
        assertThat(reminderTimeChanges).containsExactly(LocalTime.of(8, 0))
    }

    @Test
    fun timePicker_cancelReportsNothing() {
        setScreen(ThemeMode.SYSTEM, reminderEnabled = true)
        composeRule.onNodeWithTag("reminder-time-row").performScrollTo().performClick()
        composeRule.onNodeWithTag("reminder-time-cancel").performClick()
        composeRule.onNodeWithTag("reminder-time-dialog").assertDoesNotExist()
        assertThat(reminderTimeChanges).isEmpty()
    }

    @Test
    fun permissionRationale_showsWhenFlagged_withSettingsAndDismissActions() {
        setScreen(ThemeMode.SYSTEM, showReminderPermissionRationale = true)
        composeRule.onNodeWithTag("reminder-permission-dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Notifications are turned off").assertIsDisplayed()
        composeRule.onNodeWithTag("reminder-permission-settings").performClick()
        assertThat(openNotificationSettingsCalls).isEqualTo(1)
    }

    @Test
    fun permissionRationale_dismissReportsDismissal() {
        setScreen(ThemeMode.SYSTEM, showReminderPermissionRationale = true)
        composeRule.onNodeWithTag("reminder-permission-dismiss").performClick()
        assertThat(rationaleDismissals).isEqualTo(1)
    }

    // --- S13 (S14: dropdown): "Open readings in" provider selector + request-an-app row. ---

    @Test
    fun providerDropdown_showsTheStoredChoice_andMarksItSelectedInTheMenu() {
        setScreen(ThemeMode.SYSTEM, selectedProvider = BibleProvider.YOUVERSION)
        composeRule.onNodeWithText("Open readings in").performScrollTo().assertIsDisplayed()
        composeRule
            .onNodeWithTag("provider-dropdown")
            .assertContentDescriptionEquals("Open readings in, YouVersion / Bible.com")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("provider-option-blb").assertIsNotSelected()
        composeRule.onNodeWithTag("provider-option-biblegateway").assertIsNotSelected()
        composeRule.onNodeWithTag("provider-option-youversion").assertIsSelected()
    }

    @Test
    fun providerDropdown_defaultsToBlueLetterBible() {
        setScreen(ThemeMode.SYSTEM)
        composeRule
            .onNodeWithTag("provider-dropdown")
            .performScrollTo()
            .assertContentDescriptionEquals("Open readings in, Blue Letter Bible (default)")
            .performClick()
        composeRule.onNodeWithTag("provider-option-blb").assertIsSelected()
    }

    @Test
    fun pickingAProviderFromTheMenu_reportsThatProvider() {
        setScreen(ThemeMode.SYSTEM)
        composeRule.onNodeWithTag("provider-dropdown").performScrollTo().performClick()
        composeRule.onNodeWithTag("provider-option-biblegateway").performClick()
        composeRule.onNodeWithTag("provider-dropdown").performClick()
        composeRule.onNodeWithTag("provider-option-youversion").performClick()
        assertThat(providerSelections)
            .containsExactly(BibleProvider.BIBLE_GATEWAY, BibleProvider.YOUVERSION)
            .inOrder()
    }

    @Test
    fun comingSoonOption_isVisibleButDisabled_andNeverReportsASelection() {
        // S14 owner request: the in-app reading teaser is render-layer only — present so
        // users know it's coming, disabled so no tap can ever persist it.
        setScreen(ThemeMode.SYSTEM)
        composeRule.onNodeWithTag("provider-dropdown").performScrollTo().performClick()
        composeRule
            .onNodeWithTag("provider-option-inapp")
            .assertIsDisplayed()
            .assertIsNotEnabled()
            .performClick()
        assertThat(providerSelections).isEmpty()
    }

    @Test
    fun requestAppRow_firesTheRequestCallback() {
        setScreen(ThemeMode.SYSTEM)
        composeRule.onNodeWithTag("request-app-row").performScrollTo().performClick()
        assertThat(requestAppCalls).isEqualTo(1)
    }
}

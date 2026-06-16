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
import com.jpillion.dailyreadingplanner.domain.model.ExternalBibleApp
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestinationMode
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
    private val modeSelections = mutableListOf<ReadingDestinationMode>()
    private val externalAppSelections = mutableListOf<ExternalBibleApp>()
    private var requestAppCalls = 0
    private val fontScaleChanges = mutableListOf<Float>()
    private val trackingStartChanges = mutableListOf<LocalDate?>()
    private val reminderToggles = mutableListOf<Boolean>()
    private val persistentToggles = mutableListOf<Boolean>()
    private val reminderTimeChanges = mutableListOf<LocalTime>()
    private var rationaleDismissals = 0
    private var openNotificationSettingsCalls = 0
    private var resetConfirms = 0
    private var backCalls = 0

    private val showStreaksToggles = mutableListOf<Boolean>()

    private val planSelections = mutableListOf<String>()
    private var planSwitchConfirms = 0
    private var planSwitchDismissals = 0

    private val twoPlanSelector =
        PlanSelectorUiState(
            options =
                listOf(
                    PlanOption(id = "bible_companion", name = "Bible Companion"),
                    PlanOption(id = "mcheyne", name = "M'Cheyne"),
                ),
            activeId = "bible_companion",
        )

    private fun setScreen(
        selectedMode: ThemeMode,
        planSelector: PlanSelectorUiState = twoPlanSelector,
        pendingPlanSwitch: PendingPlanSwitch? = null,
        destinationMode: ReadingDestinationMode = ReadingDestinationMode.EXTERNAL,
        externalBibleApp: ExternalBibleApp = ExternalBibleApp.BLB,
        mySwordInstalled: Boolean = false,
        showStreaks: Boolean = true,
        fontScale: Float = 1f,
        trackingStartDate: LocalDate? = null,
        reminderEnabled: Boolean = false,
        reminderTime: LocalTime = LocalTime.of(8, 0),
        persistentNotificationEnabled: Boolean = false,
        showReminderPermissionRationale: Boolean = false,
    ) {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                SettingsScreen(
                    selectedMode = selectedMode,
                    planSelector = planSelector,
                    pendingPlanSwitch = pendingPlanSwitch,
                    destinationMode = destinationMode,
                    externalBibleApp = externalBibleApp,
                    mySwordInstalled = mySwordInstalled,
                    showStreaks = showStreaks,
                    fontScale = fontScale,
                    currentYear = 2026,
                    trackingStartDate = trackingStartDate,
                    reminderEnabled = reminderEnabled,
                    reminderTime = reminderTime,
                    persistentNotificationEnabled = persistentNotificationEnabled,
                    showReminderPermissionRationale = showReminderPermissionRationale,
                    onThemeModeSelected = { selections += it },
                    onPlanSelected = { planSelections += it },
                    onPlanSwitchConfirmed = { planSwitchConfirms++ },
                    onPlanSwitchDismissed = { planSwitchDismissals++ },
                    onDestinationModeSelected = { modeSelections += it },
                    onExternalBibleAppSelected = { externalAppSelections += it },
                    onShowStreaksToggled = { showStreaksToggles += it },
                    onRequestApp = { requestAppCalls++ },
                    onFontScaleChanged = { fontScaleChanges += it },
                    onTrackingStartChanged = { trackingStartChanges += it },
                    onReminderToggled = { reminderToggles += it },
                    onReminderTimeChanged = { reminderTimeChanges += it },
                    onPersistentNotificationToggled = { persistentToggles += it },
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

    // --- Sprint K (D-23-1): destination-mode segmented toggle + "My Bible app" dropdown. ---

    @Test
    fun destinationModeToggle_showsBothSegments_andMarksTheStoredModeSelected() {
        setScreen(ThemeMode.SYSTEM, destinationMode = ReadingDestinationMode.IN_APP)
        composeRule.onNodeWithText("Open readings in").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("destination-mode-toggle").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("destination-mode-inapp").assertIsSelected()
        composeRule.onNodeWithTag("destination-mode-external").assertIsNotSelected()
    }

    @Test
    fun destinationModeToggle_defaultsToExternal() {
        setScreen(ThemeMode.SYSTEM)
        composeRule.onNodeWithTag("destination-mode-external").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("destination-mode-inapp").assertIsNotSelected()
    }

    @Test
    fun tappingTheInAppSegment_reportsTheInAppMode() {
        setScreen(ThemeMode.SYSTEM, destinationMode = ReadingDestinationMode.EXTERNAL)
        composeRule.onNodeWithTag("destination-mode-inapp").performScrollTo().performClick()
        assertThat(modeSelections).containsExactly(ReadingDestinationMode.IN_APP)
    }

    @Test
    fun tappingTheExternalSegment_reportsTheExternalMode() {
        setScreen(ThemeMode.SYSTEM, destinationMode = ReadingDestinationMode.IN_APP)
        composeRule.onNodeWithTag("destination-mode-external").performScrollTo().performClick()
        assertThat(modeSelections).containsExactly(ReadingDestinationMode.EXTERNAL)
    }

    @Test
    fun externalAppDropdown_isHidden_whenModeIsInApp() {
        // The "My Bible app" dropdown is only relevant in external mode.
        setScreen(ThemeMode.SYSTEM, destinationMode = ReadingDestinationMode.IN_APP)
        composeRule.onNodeWithTag("provider-dropdown").assertDoesNotExist()
    }

    @Test
    fun externalAppDropdown_showsTheStoredChoice_andMarksItSelectedInTheMenu() {
        setScreen(
            ThemeMode.SYSTEM,
            destinationMode = ReadingDestinationMode.EXTERNAL,
            externalBibleApp = ExternalBibleApp.YOUVERSION,
        )
        composeRule
            .onNodeWithTag("provider-dropdown")
            .assertContentDescriptionEquals("My Bible app, YouVersion / Bible.com")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("provider-option-blb").assertIsNotSelected()
        composeRule.onNodeWithTag("provider-option-biblegateway").assertIsNotSelected()
        composeRule.onNodeWithTag("provider-option-youversion").assertIsSelected()
    }

    @Test
    fun externalAppDropdown_defaultsToBlueLetterBible() {
        setScreen(ThemeMode.SYSTEM, destinationMode = ReadingDestinationMode.EXTERNAL)
        composeRule
            .onNodeWithTag("provider-dropdown")
            .performScrollTo()
            .assertContentDescriptionEquals("My Bible app, Blue Letter Bible (default)")
            .performClick()
        composeRule.onNodeWithTag("provider-option-blb").assertIsSelected()
    }

    @Test
    fun pickingAnExternalAppFromTheMenu_reportsThatApp() {
        setScreen(ThemeMode.SYSTEM, destinationMode = ReadingDestinationMode.EXTERNAL)
        composeRule.onNodeWithTag("provider-dropdown").performScrollTo().performClick()
        composeRule.onNodeWithTag("provider-option-biblegateway").performClick()
        composeRule.onNodeWithTag("provider-dropdown").performClick()
        composeRule.onNodeWithTag("provider-option-youversion").performClick()
        assertThat(externalAppSelections)
            .containsExactly(ExternalBibleApp.BIBLE_GATEWAY, ExternalBibleApp.YOUVERSION)
            .inOrder()
    }

    // --- S15: MySword install-detected app option (D-S15-2) ---

    @Test
    fun mySwordOption_whenNotInstalled_isVisibleButDisabled_withTheNotInstalledLabel() {
        // Owner UX: mirror the S14 teaser idiom — discoverable, never a dead tap.
        setScreen(ThemeMode.SYSTEM, destinationMode = ReadingDestinationMode.EXTERNAL, mySwordInstalled = false)
        composeRule.onNodeWithTag("provider-dropdown").performScrollTo().performClick()
        composeRule
            .onNodeWithTag("provider-option-mysword")
            .assertIsDisplayed()
            .assertIsNotEnabled()
            .performClick()
        composeRule.onNodeWithText("MySword (app not installed)").assertIsDisplayed()
        assertThat(externalAppSelections).isEmpty()
    }

    @Test
    fun mySwordOption_whenInstalled_isSelectable_andReportsTheApp() {
        setScreen(ThemeMode.SYSTEM, destinationMode = ReadingDestinationMode.EXTERNAL, mySwordInstalled = true)
        composeRule.onNodeWithTag("provider-dropdown").performScrollTo().performClick()
        composeRule.onNodeWithTag("provider-option-mysword").assertIsDisplayed().performClick()
        assertThat(externalAppSelections).containsExactly(ExternalBibleApp.MYSWORD)
    }

    @Test
    fun mySwordChoice_showsInTheRow_whenSelected() {
        setScreen(
            ThemeMode.SYSTEM,
            destinationMode = ReadingDestinationMode.EXTERNAL,
            externalBibleApp = ExternalBibleApp.MYSWORD,
            mySwordInstalled = true,
        )
        composeRule
            .onNodeWithTag("provider-dropdown")
            .performScrollTo()
            .assertContentDescriptionEquals("My Bible app, MySword")
            .performClick()
        composeRule.onNodeWithTag("provider-option-mysword").assertIsSelected()
    }

    // --- S15: show-streaks toggle (D-S15-5) ---

    @Test
    fun showStreaksToggle_isOnByDefault_andReportsTheNewValue() {
        setScreen(ThemeMode.SYSTEM)
        composeRule
            .onNodeWithTag("show-streaks-toggle")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsOn()
        composeRule.onNodeWithTag("show-streaks-toggle").performClick()
        assertThat(showStreaksToggles).containsExactly(false)
    }

    @Test
    fun showStreaksHelp_explainsTheStreakRule_exactCopy() {
        // S18 (owner): the helper text under the toggle states the D-S11-2 rule in plain
        // language. Pinned LITERALLY for owner tone sign-off (PRD M8).
        setScreen(ThemeMode.SYSTEM)
        composeRule
            .onNodeWithText(
                "A streak counts consecutive days with all three readings done. " +
                    "Today doesn\u2019t end a streak until the day is over, and days before your " +
                    "tracking start date don\u2019t count against you. When this is off, " +
                    "streaks stay hidden\u2014year and stream progress still show.",
            ).assertExists()
    }

    @Test
    fun showStreaksToggle_reflectsTheOffState() {
        setScreen(ThemeMode.SYSTEM, showStreaks = false)
        composeRule.onNodeWithTag("show-streaks-toggle").performScrollTo().assertIsOff()
        composeRule.onNodeWithTag("show-streaks-toggle").performClick()
        assertThat(showStreaksToggles).containsExactly(true)
    }

    @Test
    fun requestAppRow_firesTheRequestCallback() {
        setScreen(ThemeMode.SYSTEM)
        composeRule.onNodeWithTag("request-app-row").performScrollTo().performClick()
        assertThat(requestAppCalls).isEqualTo(1)
    }

    @Test
    fun persistentToggle_isOffByDefault_andReportsTaps() {
        setScreen(ThemeMode.SYSTEM)
        composeRule.onNodeWithTag("persistent-notification-toggle").performScrollTo().assertIsOff()
        composeRule.onNodeWithTag("persistent-notification-toggle").performScrollTo().performClick()
        assertThat(persistentToggles).containsExactly(true)
    }

    @Test
    fun persistentToggle_reflectsEnabledState() {
        setScreen(ThemeMode.SYSTEM, persistentNotificationEnabled = true)
        composeRule.onNodeWithTag("persistent-notification-toggle").performScrollTo().assertIsOn()
    }

    // --- Alt Sprint D (D-ALT-18/19): the reading-plan selector row + switch dialog. ---

    @Test
    fun planDropdownRow_showsTheActivePlanName_andSpeaksIt() {
        setScreen(ThemeMode.SYSTEM)
        composeRule.onNodeWithText("Reading plan").assertIsDisplayed()
        composeRule
            .onNodeWithTag("plan-dropdown")
            .performScrollTo()
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Reading plan, Bible Companion")
        // The menu is closed until the row is tapped.
        composeRule.onNodeWithTag("plan-option-mcheyne").assertDoesNotExist()
    }

    @Test
    fun openingThePlanMenu_marksTheActivePlanSelected() {
        setScreen(ThemeMode.SYSTEM)
        composeRule.onNodeWithTag("plan-dropdown").performScrollTo().performClick()
        composeRule.onNodeWithTag("plan-option-bible_companion").assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithTag("plan-option-mcheyne").assertIsNotSelected()
    }

    @Test
    fun pickingADifferentPlan_reportsItToThePlanSelectedCallback() {
        setScreen(ThemeMode.SYSTEM)
        composeRule.onNodeWithTag("plan-dropdown").performScrollTo().performClick()
        composeRule.onNodeWithTag("plan-option-mcheyne").performClick()
        assertThat(planSelections).containsExactly("mcheyne")
    }

    @Test
    fun theSwitchDialog_explainsTheNonDestructiveSwitch_andRoutesConfirm() {
        setScreen(
            ThemeMode.SYSTEM,
            pendingPlanSwitch =
                PendingPlanSwitch(toId = "mcheyne", fromName = "Bible Companion", toName = "M'Cheyne"),
        )
        composeRule.onNodeWithTag("plan-switch-dialog").assertIsDisplayed()
        // The copy names both plans and promises the saved progress (D-ALT-19).
        composeRule.onNodeWithText("Switch to M'Cheyne?").assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "Your Bible Companion progress is saved — switch back any time and it'll be here. " +
                    "M'Cheyne starts fresh.",
            ).assertIsDisplayed()
        composeRule.onNodeWithTag("plan-switch-confirm").performClick()
        assertThat(planSwitchConfirms).isEqualTo(1)
        assertThat(planSwitchDismissals).isEqualTo(0)
    }

    @Test
    fun theSwitchDialog_cancelRoutesDismiss() {
        setScreen(
            ThemeMode.SYSTEM,
            pendingPlanSwitch =
                PendingPlanSwitch(toId = "mcheyne", fromName = "Bible Companion", toName = "M'Cheyne"),
        )
        composeRule.onNodeWithTag("plan-switch-cancel").performClick()
        assertThat(planSwitchDismissals).isEqualTo(1)
        assertThat(planSwitchConfirms).isEqualTo(0)
    }

    @Test
    fun noPendingSwitch_hidesTheDialog() {
        setScreen(ThemeMode.SYSTEM, pendingPlanSwitch = null)
        composeRule.onNodeWithTag("plan-switch-dialog").assertDoesNotExist()
    }
}

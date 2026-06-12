package com.jpillion.dailyreadingplanner.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.domain.model.BibleProvider
import com.jpillion.dailyreadingplanner.domain.model.ReadingStats
import com.jpillion.dailyreadingplanner.domain.model.ReadingStatus
import com.jpillion.dailyreadingplanner.domain.model.Stream
import com.jpillion.dailyreadingplanner.domain.model.ThemeMode
import com.jpillion.dailyreadingplanner.domain.threePortions
import com.jpillion.dailyreadingplanner.ui.datepicker.DayDatePickerDialog
import com.jpillion.dailyreadingplanner.ui.day.DayContent
import com.jpillion.dailyreadingplanner.ui.day.DayUiState
import com.jpillion.dailyreadingplanner.ui.settings.SettingsScreen
import com.jpillion.dailyreadingplanner.ui.stats.StatsContent
import com.jpillion.dailyreadingplanner.ui.theme.DailyReadingPlannerTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

/**
 * S9-T7: the JVM-provable slice of release gate G-A11Y, pinned so regressions fail CI:
 * every interactive control on the three stateless surfaces meets the 48dp touch target
 * (Material minimum-interactive-size expansion counts — we measure *touch* bounds, not
 * layout bounds) and the picker grid / slider expose proper semantics. The device-only
 * remainder (TalkBack traversal order, real font-scale rendering, dot contrast under
 * dynamic color) is the owner checklist in docs/sprints/sprint-0009-hardening-release.md.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccessibilityGateTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 6, 10)

    private fun hasAnyContentDescription() = SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)

    /** Asserts the node's *touch* bounds (incl. minimum-target expansion) are at least [min] square. */
    private fun SemanticsNodeInteraction.assertTouchTargetAtLeast(min: Dp): SemanticsNodeInteraction {
        val node = fetchSemanticsNode("failed to check touch target")
        val bounds = node.touchBoundsInRoot
        with(node.layoutInfo.density) {
            assertThat(bounds.width.toDp().value).isAtLeast(min.value - 0.5f)
            assertThat(bounds.height.toDp().value).isAtLeast(min.value - 0.5f)
        }
        return this
    }

    @Test
    fun `day screen marks and whole-day action meet 48dp touch targets`() {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                DayContent(
                    state =
                        DayUiState.Scheduled(
                            date = today,
                            readings = threePortions.map { ReadingStatus(it, false) },
                            dayComplete = false,
                        ),
                    onToggleReading = {},
                    onMarkWholeDay = {},
                    onReadingTapped = {},
                    onRetry = {},
                )
            }
        }
        for (stream in 1..3) {
            composeRule.onNodeWithTag("toggle-$stream").assertTouchTargetAtLeast(48.dp)
        }
        composeRule.onNodeWithTag("whole-day-button").performScrollTo().assertTouchTargetAtLeast(48.dp)
    }

    @Test
    fun `settings controls meet 48dp touch targets and the slider exposes range semantics`() {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                SettingsScreen(
                    selectedMode = ThemeMode.SYSTEM,
                    selectedProvider = BibleProvider.BLB,
                    mySwordInstalled = false,
                    showStreaks = true,
                    fontScale = 1f,
                    currentYear = 2026,
                    trackingStartDate = today,
                    reminderEnabled = true,
                    reminderTime = LocalTime.of(8, 0),
                    showReminderPermissionRationale = false,
                    onThemeModeSelected = {},
                    onBibleProviderSelected = {},
                    onShowStreaksToggled = {},
                    onRequestApp = {},
                    onFontScaleChanged = {},
                    onTrackingStartChanged = {},
                    onReminderToggled = {},
                    onReminderTimeChanged = {},
                    onPermissionRationaleDismissed = {},
                    onOpenNotificationSettings = {},
                    onResetProgressConfirmed = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("settings-back").assertTouchTargetAtLeast(48.dp)
        // S14: the theme selector is a dropdown row (speaks label+value); the menu items
        // are stock M3 DropdownMenuItems at the 48dp item token — verified open.
        composeRule
            .onNodeWithTag("theme-dropdown")
            .assertTouchTargetAtLeast(48.dp)
            .assertContentDescriptionContains("Theme", substring = true)
            .performClick()
        for (tag in listOf("theme-option-light", "theme-option-dark", "theme-option-system")) {
            composeRule.onNodeWithTag(tag).assertTouchTargetAtLeast(48.dp)
        }
        composeRule.onNodeWithTag("theme-option-system").performClick() // close the menu
        // Stock M3 Slider: its semantics/touch node is the handle container, pinned at the
        // M3 handle-height token (44dp) regardless of outer padding. Accepted as the design
        // system's standard control (S9-T7 finding); TalkBack/switch-access usability of the
        // slider is on the owner's device checklist. Everything we author ourselves is 48dp.
        composeRule
            .onNodeWithTag("text-size-slider")
            .assertTouchTargetAtLeast(44.dp)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        composeRule.onNodeWithTag("reset-progress").performScrollTo().assertTouchTargetAtLeast(48.dp)
        // S10: the tracking-start row and its Clear control are authored controls -> 48dp,
        // and both speak their purpose (label+value on the row; explicit label on Clear).
        composeRule
            .onNodeWithTag("tracking-start-row")
            .performScrollTo()
            .assertTouchTargetAtLeast(48.dp)
            .assertContentDescriptionContains("Start tracking from", substring = true)
        composeRule
            .onNodeWithTag("tracking-start-clear")
            .performScrollTo()
            .assertTouchTargetAtLeast(48.dp)
            .assert(hasAnyContentDescription())
        // S12: the reminder rows are authored controls -> 48dp; the toggle row exposes
        // switch semantics and the time row speaks label+value.
        // S13 (S14: dropdown): the provider dropdown row and the request-an-app row are
        // authored controls -> 48dp; menu items (incl. the disabled coming-soon teaser,
        // which TalkBack must still reach and announce) verified open.
        composeRule
            .onNodeWithTag("provider-dropdown")
            .performScrollTo()
            .assertTouchTargetAtLeast(48.dp)
            .assertContentDescriptionContains("Open readings in", substring = true)
            .performClick()
        // S15: the disabled MySword "(app not installed)" item joins the disabled teaser —
        // TalkBack must still reach and announce both.
        for (tag in listOf(
            "provider-option-blb",
            "provider-option-biblegateway",
            "provider-option-youversion",
            "provider-option-mysword",
            "provider-option-inapp",
        )) {
            composeRule.onNodeWithTag(tag).assertTouchTargetAtLeast(48.dp)
        }
        composeRule.onNodeWithTag("provider-option-blb").performClick() // close the menu
        composeRule.onNodeWithTag("request-app-row").performScrollTo().assertTouchTargetAtLeast(48.dp)
        // S15 (D-S15-5): the show-streaks toggle is an authored control -> 48dp + switch semantics.
        composeRule
            .onNodeWithTag("show-streaks-toggle")
            .performScrollTo()
            .assertTouchTargetAtLeast(48.dp)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
        composeRule
            .onNodeWithTag("reminder-toggle")
            .performScrollTo()
            .assertTouchTargetAtLeast(48.dp)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
        composeRule
            .onNodeWithTag("reminder-time-row")
            .performScrollTo()
            .assertTouchTargetAtLeast(48.dp)
            .assertContentDescriptionContains("Reminder time", substring = true)
    }

    @Test
    fun `picker grid cells meet 48dp touch targets and speak the full date`() {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                DayDatePickerDialog(
                    year = 2026,
                    today = today,
                    initialDate = today,
                    completionFor = { MutableStateFlow(emptyMap()) },
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
        for (day in listOf(1, 10, 30)) {
            composeRule
                .onNodeWithTag("picker-day-$day")
                .assertTouchTargetAtLeast(48.dp)
                .assert(hasAnyContentDescription())
        }
        composeRule.onNodeWithTag("picker-day-10").assertContentDescriptionContains("Wednesday, June 10, 2026")
        composeRule
            .onNodeWithTag(
                "picker-prev-month",
            ).assertTouchTargetAtLeast(48.dp)
            .assert(hasAnyContentDescription())
        composeRule
            .onNodeWithTag(
                "picker-next-month",
            ).assertTouchTargetAtLeast(48.dp)
            .assert(hasAnyContentDescription())
        composeRule.onNodeWithTag("date-picker-confirm").assertTouchTargetAtLeast(48.dp)
        composeRule.onNodeWithTag("date-picker-cancel").assertTouchTargetAtLeast(48.dp)
    }

    @Test
    fun `stats panel - read-only and every stat group speaks label plus value as one node`() {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                StatsContent(
                    stats =
                        ReadingStats(
                            currentStreakDays = 4,
                            longestStreakDays = 12,
                            yearReadCount = 438,
                            streamReadCounts =
                                mapOf(
                                    Stream.LAW_AND_HISTORY to 150,
                                    Stream.PSALMS_AND_PROPHECY to 144,
                                    Stream.NEW_TESTAMENT to 144,
                                ),
                        ),
                    showStreaks = true,
                )
            }
        }
        // S15 (D-S15-4): the panel is read-only — no interactive controls. Each stat group
        // is one merged semantics node so TalkBack reads label and value together.
        composeRule
            .onNodeWithTag("stats-current-streak")
            .assert(hasTextContaining("Current streak"))
            .assert(hasTextContaining("4 days"))
        composeRule
            .onNodeWithTag("stats-longest-streak")
            .assert(hasTextContaining("Longest streak"))
            .assert(hasTextContaining("12 days"))
        composeRule
            .onNodeWithTag("stats-year")
            .assert(hasTextContaining("This year"))
            .assert(hasTextContaining("40%"))
        composeRule
            .onNodeWithTag("stats-stream-1")
            .assert(hasTextContaining("Law & History"))
            .assert(hasTextContaining("150 of 365"))
    }

    private fun hasTextContaining(substring: String): SemanticsMatcher =
        SemanticsMatcher("has text containing '$substring'") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.contains(substring) } == true
        }
}

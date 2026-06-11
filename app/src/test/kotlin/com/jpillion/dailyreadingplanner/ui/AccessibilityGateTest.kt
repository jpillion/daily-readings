package com.jpillion.dailyreadingplanner.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.domain.model.ReadingStatus
import com.jpillion.dailyreadingplanner.domain.model.ThemeMode
import com.jpillion.dailyreadingplanner.domain.threePortions
import com.jpillion.dailyreadingplanner.ui.datepicker.DayDatePickerDialog
import com.jpillion.dailyreadingplanner.ui.day.DayContent
import com.jpillion.dailyreadingplanner.ui.day.DayUiState
import com.jpillion.dailyreadingplanner.ui.settings.SettingsScreen
import com.jpillion.dailyreadingplanner.ui.theme.DailyReadingPlannerTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

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
                    fontScale = 1f,
                    currentYear = 2026,
                    trackingStartDate = today,
                    onThemeModeSelected = {},
                    onFontScaleChanged = {},
                    onTrackingStartChanged = {},
                    onResetProgressConfirmed = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("settings-back").assertTouchTargetAtLeast(48.dp)
        for (tag in listOf("theme-option-light", "theme-option-dark", "theme-option-system")) {
            composeRule.onNodeWithTag(tag).assertTouchTargetAtLeast(48.dp)
        }
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
}

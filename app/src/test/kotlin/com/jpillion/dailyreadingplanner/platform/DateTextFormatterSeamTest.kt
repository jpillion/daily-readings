package com.jpillion.dailyreadingplanner.platform

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import com.jpillion.dailyreadingplanner.domain.model.DayCompletion
import com.jpillion.dailyreadingplanner.domain.model.ExternalBibleApp
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestinationMode
import com.jpillion.dailyreadingplanner.domain.model.StripDayState
import com.jpillion.dailyreadingplanner.domain.model.ThemeMode
import com.jpillion.dailyreadingplanner.testing.bcReadingStats
import com.jpillion.dailyreadingplanner.testing.bcYearStrips
import com.jpillion.dailyreadingplanner.ui.datepicker.DayDatePickerDialog
import com.jpillion.dailyreadingplanner.ui.day.TrackingStartPromptDialog
import com.jpillion.dailyreadingplanner.ui.day.formatDayDate
import com.jpillion.dailyreadingplanner.ui.day.formatMonthDay
import com.jpillion.dailyreadingplanner.ui.settings.PlanOption
import com.jpillion.dailyreadingplanner.ui.settings.PlanSelectorUiState
import com.jpillion.dailyreadingplanner.ui.settings.SettingsScreen
import com.jpillion.dailyreadingplanner.ui.stats.StatsContent
import com.jpillion.dailyreadingplanner.ui.theme.DailyReadingPlannerTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * p1-01: proves that the UI actually *routes through* [DateTextFormatter] rather than
 * formatting inline.
 *
 * This is the pin whose absence would be silent. Every existing literal-string test
 * ("Today – June 10", "Jun 3, 2026", "June 2026", "438 of 1,095 readings") passes equally well
 * whether the composable calls the seam or formats inline with the same expression — they
 * cannot tell the difference, which is exactly why the extraction needs its own evidence.
 * Injecting [FakeDateTextFormatter] makes the difference visible: the marker text can only
 * appear if the call went through the interface.
 *
 * These tests deliberately assert on the *fake's* output, never on the Android
 * implementation's — [DateTextFormatter]'s contract forbids the latter.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DateTextFormatterSeamTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val fake = FakeDateTextFormatter()
    private val june10 = LocalDate.of(2026, 6, 10)

    // ---- The date picker: monthYear, weekdayInitial, firstDayOfWeek, fullDate ----

    private fun setPicker(formatter: DateTextFormatter) {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                DayDatePickerDialog(
                    today = june10,
                    initialDate = june10,
                    completionFor = { MutableStateFlow(emptyMap<LocalDate, DayCompletion>()) },
                    onConfirm = {},
                    onDismiss = {},
                    formatter = formatter,
                )
            }
        }
    }

    @Test
    fun datePickerMonthHeadingComesFromTheSeam() {
        setPicker(fake)
        composeRule.onNodeWithTag("picker-month-title").assertTextEquals("MONTHYEAR(2026-6)")
    }

    @Test
    fun datePickerDayCellSpokenDateComesFromTheSeam() {
        setPicker(fake)
        // The a11y gate pins this cell's spoken text; here we prove where the text comes from.
        composeRule.onNodeWithContentDescription("FULL(2026-06-10)").assertExists()
    }

    @Test
    fun datePickerWeekdayHeaderComesFromTheSeam() {
        setPicker(fake)
        // Seven column headers, all from weekdayInitial. Sunday-first per the fake.
        composeRule.onNodeWithText("WD(7)").assertExists()
        composeRule.onNodeWithText("WD(1)").assertExists()
    }

    /** The weekday column labels, in the order the header row lays them out. */
    private fun weekdayHeaderLabels(): List<String> =
        composeRule
            .onAllNodesWithText("WD(", substring = true)
            .fetchSemanticsNodes()
            .map { node ->
                node.config
                    .getOrNull(SemanticsProperties.Text)
                    .orEmpty()
                    .joinToString("") { it.text }
            }

    @Test
    fun datePickerWeekColumnsStartAtTheSeamsFirstDayOfWeek() {
        // Only firstDayOfWeek() can change this order, so it proves the grid consults the seam
        // rather than a locale it read for itself.
        setPicker(FakeDateTextFormatter(firstDay = DayOfWeek.MONDAY))
        assertThat(weekdayHeaderLabels())
            .isEqualTo(listOf("WD(1)", "WD(2)", "WD(3)", "WD(4)", "WD(5)", "WD(6)", "WD(7)"))
    }

    @Test
    fun datePickerWeekColumnsFollowADifferentSeamFirstDayOfWeek() {
        setPicker(FakeDateTextFormatter(firstDay = DayOfWeek.SUNDAY))
        assertThat(weekdayHeaderLabels())
            .isEqualTo(listOf("WD(7)", "WD(1)", "WD(2)", "WD(3)", "WD(4)", "WD(5)", "WD(6)"))
    }

    // ---- The first-run prompt: mediumDate ----

    @Test
    fun trackingStartPromptDateComesFromTheSeam() {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                TrackingStartPromptDialog(
                    today = june10,
                    onChoose = {},
                    onDismiss = {},
                    formatter = fake,
                )
            }
        }
        composeRule.onNodeWithText("Start from today (MEDIUM(2026-06-10))").assertExists()
    }

    // ---- The stats panel: integer ----

    @Test
    fun statsYearCountComesFromTheSeam() {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                StatsContent(
                    stats =
                        bcReadingStats(
                            currentStreakDays = 4,
                            longestStreakDays = 12,
                            yearReadCount = 438,
                            streamReadCounts = mapOf(1 to 150, 2 to 144, 3 to 144),
                        ),
                    strips =
                        bcYearStrips(year = 2026, todayIndex = 160) {
                            List(365) { StripDayState.NEUTRAL }
                        },
                    showStreaks = true,
                    formatter = fake,
                )
            }
        }
        composeRule.onNodeWithText("INT(438) of INT(1095) readings").assertExists()
    }

    // ---- Settings: timeOfDay and mediumDate ----

    private fun setSettings(
        trackingStartDate: LocalDate?,
        formatter: DateTextFormatter = fake,
    ) {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                SettingsScreen(
                    selectedMode = ThemeMode.SYSTEM,
                    planSelector =
                        PlanSelectorUiState(
                            options = listOf(PlanOption(id = "bible_companion", name = "Bible Companion")),
                            activeId = "bible_companion",
                        ),
                    pendingPlanSwitch = null,
                    destinationMode = ReadingDestinationMode.EXTERNAL,
                    externalBibleApp = ExternalBibleApp.BLB,
                    mySwordInstalled = false,
                    showStreaks = true,
                    fontScale = 1f,
                    currentYear = 2026,
                    trackingStartDate = trackingStartDate,
                    reminderEnabled = true,
                    reminderTime = LocalTime.of(21, 30),
                    persistentNotificationEnabled = false,
                    showReminderPermissionRationale = false,
                    onThemeModeSelected = {},
                    onPlanSelected = {},
                    onPlanSwitchConfirmed = {},
                    onPlanSwitchDismissed = {},
                    onDestinationModeSelected = {},
                    onExternalBibleAppSelected = {},
                    onShowStreaksToggled = {},
                    onRequestApp = {},
                    onFontScaleChanged = {},
                    onTrackingStartChanged = {},
                    onReminderToggled = {},
                    onReminderTimeChanged = {},
                    onPersistentNotificationToggled = {},
                    onPermissionRationaleDismissed = {},
                    onOpenNotificationSettings = {},
                    onResetProgressConfirmed = {},
                    onBack = {},
                    formatter = formatter,
                )
            }
        }
    }

    @Test
    fun settingsReminderTimeComesFromTheSeam() {
        setSettings(trackingStartDate = null)
        composeRule
            .onNodeWithTag("reminder-time-value", useUnmergedTree = true)
            .performScrollTo()
            .assertTextEquals("TIME(21:30)")
    }

    @Test
    fun settingsTrackingStartDateComesFromTheSeam() {
        setSettings(trackingStartDate = LocalDate.of(2026, 6, 3))
        composeRule
            .onNodeWithTag("tracking-start-value", useUnmergedTree = true)
            .performScrollTo()
            .assertTextEquals("MEDIUM(2026-06-03)")
    }

    // ---- uses24HourTime: the tenth member, a device setting rather than formatted text ----

    private fun openReminderTimePicker() {
        composeRule.onNodeWithTag("reminder-time-row").performScrollTo().performClick()
        composeRule.onNodeWithTag("reminder-time-dialog").assertExists()
    }

    @Test
    fun reminderTimePickerUses12HourInputWhenTheSeamSaysSo() {
        setSettings(trackingStartDate = null, formatter = FakeDateTextFormatter(uses24HourTime = false))
        openReminderTimePicker()
        // A 12-hour M3 TimePicker carries the AM/PM selector; a 24-hour one does not.
        composeRule.onNodeWithText("AM").assertExists()
    }

    @Test
    fun reminderTimePickerUses24HourInputWhenTheSeamSaysSo() {
        setSettings(trackingStartDate = null, formatter = FakeDateTextFormatter(uses24HourTime = true))
        openReminderTimePicker()
        composeRule.onNodeWithText("AM").assertDoesNotExist()
    }

    @Test
    fun androidImplementationReadsTheDeviceTimeFormatSetting() {
        // The one member that is device state, not formatting: it must track Settings.System
        // TIME_12_24 rather than the locale. Asserting both directions rules out a frozen or
        // inverted boolean, neither of which the UI pins above could distinguish.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val formatter = AndroidDateTextFormatter(context)

        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
        assertThat(formatter.uses24HourTime).isTrue()

        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "12")
        assertThat(formatter.uses24HourTime).isFalse()
    }

    // ---- D-S16-1 stays in the composable layer (acceptance criterion 4) ----

    @Test
    fun titleHelpersTakeOnlyTheLocalizedFragmentFromTheSeam() {
        assertThat(formatMonthDay(june10, fake)).isEqualTo("MONTHDAY(2026-06-10)")
        // The year-append RULE is product logic and lives here, not in the platform
        // implementation: same seam output, different suffix, decided by the composable layer.
        assertThat(formatDayDate(june10, todayYear = 2026, formatter = fake))
            .isEqualTo("WEEKDAYMONTHDAY(2026-06-10)")
        assertThat(formatDayDate(june10, todayYear = 2025, formatter = fake))
            .isEqualTo("WEEKDAYMONTHDAY(2026-06-10), 2026")
    }

    // ---- The Android implementation: shape only, never exact text ----

    @Test
    fun androidImplementationKeepsTheNineOutputsDistinct() {
        // DateTextFormatter forbids asserting on exact output, so this asserts the property a
        // copy-paste slip would break: the differently-styled forms must not collapse into one
        // another. (AC5's mutation — mediumDate given fullDate's body — is caught by the
        // literal "Jun 3, 2026" / "Start from today (Jun 10, 2026)" pins.)
        val impl = AndroidDateTextFormatter(ApplicationProvider.getApplicationContext())
        assertThat(impl.mediumDate(june10)).isNotEqualTo(impl.fullDate(june10))
        assertThat(impl.monthDay(june10)).isNotEqualTo(impl.weekdayMonthDay(june10))
        assertThat(impl.monthYear(june10)).isNotEqualTo(impl.monthDay(june10))
        // weekdayMonthDay carries the weekday; monthDay must not.
        assertThat(impl.weekdayMonthDay(june10)).contains(impl.monthDay(june10))
    }
}

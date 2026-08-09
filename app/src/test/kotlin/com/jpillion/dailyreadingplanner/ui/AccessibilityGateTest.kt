package com.jpillion.dailyreadingplanner.ui

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.bible.domain.model.BibleTranslation
import com.jpillion.dailyreadingplanner.bible.domain.model.ChapterContent
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseId
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseText
import com.jpillion.dailyreadingplanner.bible.ui.picker.BookChapterPicker
import com.jpillion.dailyreadingplanner.bible.ui.reader.ReaderScreen
import com.jpillion.dailyreadingplanner.bible.ui.reader.ReaderUiState
import com.jpillion.dailyreadingplanner.bible.ui.reader.ReaderVersionState
import com.jpillion.dailyreadingplanner.bible.ui.reader.VerseActionMenu
import com.jpillion.dailyreadingplanner.bible.ui.reader.VerseSelection
import com.jpillion.dailyreadingplanner.bible.ui.reader.VerseSelectionBar
import com.jpillion.dailyreadingplanner.bible.ui.reader.cleared
import com.jpillion.dailyreadingplanner.bible.ui.reader.toggle
import com.jpillion.dailyreadingplanner.domain.model.ExternalBibleApp
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestinationMode
import com.jpillion.dailyreadingplanner.domain.model.StripDayState
import com.jpillion.dailyreadingplanner.domain.model.ThemeMode
import com.jpillion.dailyreadingplanner.domain.threePortions
import com.jpillion.dailyreadingplanner.testing.bcReadingStats
import com.jpillion.dailyreadingplanner.testing.bcYearStrips
import com.jpillion.dailyreadingplanner.testing.singleSegmentStates
import com.jpillion.dailyreadingplanner.ui.datepicker.DayDatePickerDialog
import com.jpillion.dailyreadingplanner.ui.day.DayContent
import com.jpillion.dailyreadingplanner.ui.day.DayUiState
import com.jpillion.dailyreadingplanner.ui.day.TrackingStartPromptDialog
import com.jpillion.dailyreadingplanner.ui.navigation.UpdateRestartSnackbarEffect
import com.jpillion.dailyreadingplanner.ui.settings.PlanOption
import com.jpillion.dailyreadingplanner.ui.settings.PlanSelectorUiState
import com.jpillion.dailyreadingplanner.ui.settings.SettingsScreen
import com.jpillion.dailyreadingplanner.ui.stats.StatsContent
import com.jpillion.dailyreadingplanner.ui.theme.DailyReadingPlannerTheme
import com.jpillion.dailyreadingplanner.update.UpdatePhase
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

    /** Q2: the announced label for the node's click action ("what a tap will do right now"). */
    private fun hasClickLabel(expected: String) =
        SemanticsMatcher("onClick label == '$expected'") { node ->
            node.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnClick)?.label == expected
        }

    /** Q2: the announced label for the node's long-click action — the TalkBack-visible way in. */
    private fun hasLongClickLabel(expected: String) =
        SemanticsMatcher("onLongClick label == '$expected'") { node ->
            node.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnLongClick)?.label == expected
        }

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
                            // sprint-00P: one card per segment; a Bible-Companion reading is a
                            // single segment, so the tags gain a "-0" index.
                            segments = singleSegmentStates(threePortions),
                            dayComplete = false,
                        ),
                    onToggleSegment = {},
                    onSegmentTapped = {},
                    onRetry = {},
                )
            }
        }
        for (stream in 1..3) {
            composeRule.onNodeWithTag("toggle-$stream-0").assertTouchTargetAtLeast(48.dp)
        }
    }

    @Test
    fun `settings controls meet 48dp touch targets and the slider exposes range semantics`() {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                SettingsScreen(
                    selectedMode = ThemeMode.SYSTEM,
                    planSelector =
                        PlanSelectorUiState(
                            options =
                                listOf(
                                    PlanOption(id = "bible_companion", name = "Bible Companion"),
                                    PlanOption(id = "mcheyne", name = "M'Cheyne"),
                                ),
                            activeId = "bible_companion",
                        ),
                    pendingPlanSwitch = null,
                    destinationMode = ReadingDestinationMode.EXTERNAL,
                    externalBibleApp = ExternalBibleApp.BLB,
                    mySwordInstalled = false,
                    showStreaks = true,
                    fontScale = 1f,
                    currentYear = 2026,
                    trackingStartDate = today,
                    reminderEnabled = true,
                    reminderTime = LocalTime.of(8, 0),
                    persistentNotificationEnabled = true,
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
                )
            }
        }
        composeRule.onNodeWithTag("settings-back").assertTouchTargetAtLeast(48.dp)
        // Alt Sprint D (D-ALT-18): the reading-plan selector is an authored 56dp dropdown
        // row that speaks "Reading plan, <value>" — pinned ≥48dp + spoken here.
        composeRule
            .onNodeWithTag("plan-dropdown")
            .assertTouchTargetAtLeast(48.dp)
            .assertContentDescriptionContains("Reading plan", substring = true)
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
        // Sprint K (D-23-1): the destination-mode segmented toggle and the "My Bible app" dropdown
        // are authored controls -> 48dp. The toggle row speaks "Open readings in, <value>" and each
        // segment carries RadioButton selection semantics; the dropdown row speaks "My Bible app, …".
        composeRule
            .onNodeWithTag("destination-mode-toggle")
            .performScrollTo()
            .assertTouchTargetAtLeast(48.dp)
            .assertContentDescriptionContains("Open readings in", substring = true)
        for (tag in listOf("destination-mode-inapp", "destination-mode-external")) {
            composeRule.onNodeWithTag(tag).assertTouchTargetAtLeast(48.dp)
        }
        composeRule
            .onNodeWithTag("provider-dropdown")
            .performScrollTo()
            .assertTouchTargetAtLeast(48.dp)
            .assertContentDescriptionContains("My Bible app", substring = true)
            .performClick()
        // S15: every external-app item — including the install-gated MySword item (here disabled,
        // which TalkBack must still reach and announce) — must be reachable with a 48dp touch target.
        for (tag in listOf(
            "provider-option-blb",
            "provider-option-biblegateway",
            "provider-option-youversion",
            "provider-option-mysword",
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
            .onNodeWithTag("persistent-notification-toggle")
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
    fun `tracking-start prompt options meet 48dp touch targets and speak their full labels`() {
        // S19: the first-run prompt's three option rows are authored controls -> 48dp; each
        // row's full label (including the concrete date) is its text, so TalkBack speaks the
        // actual choice, never a bare icon or color.
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                TrackingStartPromptDialog(today = today, onChoose = {}, onDismiss = {})
            }
        }
        for (tag in listOf("tracking-prompt-jan1", "tracking-prompt-today", "tracking-prompt-custom")) {
            composeRule.onNodeWithTag(tag).assertTouchTargetAtLeast(48.dp)
        }
    }

    @Test
    fun `picker grid cells meet 48dp touch targets and speak the full date`() {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                DayDatePickerDialog(
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
        // One-tap select (BACKLOG #7): no confirm button; Cancel remains a 48dp target.
        composeRule.onNodeWithTag("date-picker-cancel").assertTouchTargetAtLeast(48.dp)
    }

    @Test
    fun `stats panel - read-only and every stat group speaks label plus value as one node`() {
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
                    strips = bcYearStrips(year = 2026, todayIndex = 160) { List(365) { StripDayState.NEUTRAL } },
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

    @Test
    fun `reader tappable verses and picker action meet 48dp touch targets`() {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                val state =
                    ReaderUiState.Content(
                        blocks =
                            listOf(
                                ChapterContent(
                                    bookNo = 19,
                                    bookName = "Psalms",
                                    chapter = 23,
                                    verses =
                                        listOf(
                                            VerseText(
                                                VerseId.encode(19, 23, 0),
                                                "",
                                                isTitle = true,
                                                markup = "A Psalm of David.",
                                            ),
                                            VerseText(
                                                VerseId.encode(19, 23, 1),
                                                "1",
                                                isTitle = false,
                                                markup = "The <a>LORD</a> is my shepherd",
                                            ),
                                        ),
                                ),
                            ),
                        title = "Psalms 23",
                    )
                ReaderScreen(
                    pagerState =
                        androidx.compose.foundation.pager
                            .rememberPagerState(initialPage = 0) { 1 },
                    stateForPage = { state },
                    externalApp = com.jpillion.dailyreadingplanner.domain.model.ExternalBibleApp.BLB,
                    versionState =
                        com.jpillion.dailyreadingplanner.bible.ui.reader.ReaderVersionState(
                            available =
                                listOf(
                                    com.jpillion.dailyreadingplanner.bible.domain.model
                                        .BibleTranslation("KJV", "King James Version"),
                                ),
                            selected =
                                com.jpillion.dailyreadingplanner.bible.domain.model
                                    .BibleTranslation("KJV", "King James Version"),
                        ),
                    selection = VerseSelection.NONE,
                    onSelectVersion = {},
                    onOpenPicker = {},
                    onVerseLongPressed = { _, _ -> },
                    onVerseSelectionToggled = { _, _ -> },
                    onOpenVerseExternally = {},
                    onCopyVerse = { _, _ -> },
                    onCopySelection = {},
                    onClearSelection = {},
                    onRetry = {},
                )
            }
        }
        // H7: each tappable verse is a >=48dp target (and the picker action stays a target).
        composeRule.onNodeWithTag("reader-verse-${VerseId.encode(19, 23, 1)}").assertTouchTargetAtLeast(48.dp)
        composeRule.onNodeWithTag("reader-open-picker").assertTouchTargetAtLeast(48.dp)
        // The superscription carries heading semantics and speaks the plain (stripped) title.
        composeRule
            .onNodeWithTag("reader-title-${VerseId.encode(19, 23, 0)}")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assertContentDescriptionContains("A Psalm of David", substring = true)
        // The verse speaks stripped markup (added word kept, tags gone), never the raw tags.
        composeRule
            .onNodeWithTag("reader-verse-${VerseId.encode(19, 23, 1)}")
            .assertContentDescriptionContains("The LORD is my shepherd", substring = true)
        // Q2: a verse announces what its gestures do. Long-press is NOT reliably reachable under
        // TalkBack, so the long-click label ("Select verses") plus the menu item of the same name
        // are the required equivalent paths into selection; the click label says what a tap does.
        composeRule
            .onNodeWithTag("reader-verse-${VerseId.encode(19, 23, 1)}")
            .assert(hasClickLabel("Open verse actions"))
            .assert(hasLongClickLabel("Select verses"))
    }

    @Test
    fun `Q2 verse action menu items meet 48dp touch targets and are spoken words`() {
        // The menu is the reader's new tap destination; every item is an authored interactive
        // control, so each must be a >=48dp target with a visible (spoken) label — never a glyph.
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                VerseActionMenu(
                    expanded = true,
                    externalApp = com.jpillion.dailyreadingplanner.domain.model.ExternalBibleApp.BLB,
                    onDismissRequest = {},
                    onOpenExternally = {},
                    onCopy = {},
                    onSelectVerses = {},
                )
            }
        }
        for (tag in listOf("verse-menu-open", "verse-menu-copy", "verse-menu-select")) {
            composeRule.onNodeWithTag(tag).assertTouchTargetAtLeast(48.dp)
        }
        composeRule.onNodeWithText("Open in Blue Letter Bible").assertIsDisplayed()
        composeRule.onNodeWithText("Copy this verse").assertIsDisplayed()
        composeRule.onNodeWithText("Select verses").assertIsDisplayed()
    }

    @Test
    fun `Q2 selection action bar controls meet 48dp touch targets and the count is a live region`() {
        // The contextual action bar replaces the reader top bar while selecting: the X is a labelled
        // icon button, Copy is a VISIBLE WORD (Icons.Filled.ContentCopy does not exist in the frozen
        // icons-core, and a word beats a guessed glyph anyway — the "Restart" snackbar precedent),
        // and the count is announced politely on entry and on every change.
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                VerseSelectionBar(count = 2, onCopy = {}, onClose = {})
            }
        }
        composeRule.onNodeWithTag("verse-selection-close").assertTouchTargetAtLeast(48.dp)
        composeRule.onNodeWithTag("verse-selection-copy").assertTouchTargetAtLeast(48.dp)
        composeRule
            .onNodeWithTag("verse-selection-close")
            .assertContentDescriptionContains("Exit selection", substring = true)
        composeRule.onNodeWithText("Copy").assertIsDisplayed()
        // POLITE specifically: entering selection mode must be announced without interrupting
        // whatever TalkBack is already saying (Assertive would talk over the verse being read).
        composeRule
            .onNodeWithTag("verse-selection-count")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    // --- Q2 end-to-end a11y: the menu and the action bar as the USER reaches them. ---
    //
    // The two tests above compose VerseActionMenu / VerseSelectionBar directly, which pins the
    // controls but not that they are reachable. These drive the real flow through ReaderScreen with
    // the selection state hoisted the way ReaderRoute hoists it into the ViewModel, so a wiring
    // break (a tap that opens nothing, a menu item that fires a callback nobody honours) fails the
    // a11y gate rather than passing it on a technicality.

    private val psalm23Title = VerseId.encode(19, 23, 0)
    private val psalm23Verse1 = VerseId.encode(19, 23, 1)
    private val psalm23Verse2 = VerseId.encode(19, 23, 2)

    private fun psalm23Content(): ReaderUiState.Content =
        ReaderUiState.Content(
            blocks =
                listOf(
                    ChapterContent(
                        bookNo = 19,
                        bookName = "Psalms",
                        chapter = 23,
                        verses =
                            listOf(
                                VerseText(psalm23Title, "", isTitle = true, markup = "A Psalm of David."),
                                VerseText(
                                    psalm23Verse1,
                                    "1",
                                    isTitle = false,
                                    markup = "The <a>LORD</a> is my shepherd",
                                ),
                                VerseText(psalm23Verse2, "2", isTitle = false, markup = "He maketh me to lie down"),
                            ),
                    ),
                ),
            title = "Psalm 23",
        )

    /**
     * Composes the reader with its [VerseSelection] HOISTED into the test — the same contract
     * [com.jpillion.dailyreadingplanner.bible.ui.reader.ReaderRoute] gives it from the ViewModel —
     * so selection genuinely turns on and the contextual action bar genuinely appears.
     */
    private fun setStatefulReader(initialSelection: VerseSelection = VerseSelection.NONE) {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                var selection by remember { mutableStateOf(initialSelection) }
                val kjv = BibleTranslation("KJV", "King James Version")
                ReaderScreen(
                    pagerState = rememberPagerState(initialPage = 0) { 1 },
                    stateForPage = { psalm23Content() },
                    externalApp = ExternalBibleApp.BLB,
                    versionState = ReaderVersionState(available = listOf(kjv), selected = kjv),
                    selection = selection,
                    onOpenPicker = {},
                    onSelectVersion = {},
                    onVerseLongPressed = { page, id -> selection = VerseSelection.start(page, id) },
                    onVerseSelectionToggled = { page, id -> selection = selection.toggle(page, id) },
                    onOpenVerseExternally = {},
                    onCopyVerse = { _, _ -> },
                    onCopySelection = {},
                    onClearSelection = { selection = selection.cleared() },
                    onRetry = {},
                )
            }
        }
    }

    @Test
    fun `Q2 the menu a real verse tap opens is fully reachable - three items - 48dp - spoken words`() {
        // Reached the way the user reaches it: a short tap on a verse while nothing is selected.
        // A menu that never opens would silently pass the direct-composition pin above.
        setStatefulReader()
        composeRule.onNodeWithTag("reader-verse-$psalm23Verse1").performClick()
        composeRule.waitForIdle()
        for (tag in listOf("verse-menu-open", "verse-menu-copy", "verse-menu-select")) {
            composeRule.onNodeWithTag(tag).assertIsDisplayed().assertTouchTargetAtLeast(48.dp)
        }
        // Every item is a spoken WORD, never a bare glyph.
        composeRule.onNodeWithText("Open in Blue Letter Bible").assertIsDisplayed()
        composeRule.onNodeWithText("Copy this verse").assertIsDisplayed()
        composeRule.onNodeWithText("Select verses").assertIsDisplayed()
    }

    @Test
    fun `Q2 the Select verses menu item genuinely enters selection mode - the TalkBack path`() {
        // THE spec requirement: long-press is not reliably reachable under TalkBack, so this menu
        // item is the equivalent path into selection — and it must actually get there, not merely
        // fire a callback. Proven end-to-end: the contextual action bar appears, the tapped verse
        // is `selected`, and it SPEAKS that state.
        setStatefulReader()
        composeRule.onNodeWithTag("verse-selection-count").assertDoesNotExist()
        composeRule.onNodeWithTag("reader-verse-$psalm23Verse1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("verse-menu-select").performClick()
        composeRule.waitForIdle()

        composeRule
            .onNodeWithTag("verse-selection-count")
            .assertTextEquals("1 selected")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        composeRule.onNodeWithTag("verse-selection-close").assertTouchTargetAtLeast(48.dp)
        composeRule.onNodeWithTag("verse-selection-copy").assertTouchTargetAtLeast(48.dp)
        composeRule
            .onNodeWithTag("reader-verse-$psalm23Verse1")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "selected"))
    }

    @Test
    fun `Q2 while selecting every verse carries selected semantics and a state description`() {
        // Never colour alone: the highlight is decoration, `selected` + stateDescription are the
        // mechanism — and BOTH states are spoken, so a screen-reader user can tell them apart.
        setStatefulReader(initialSelection = VerseSelection.start(page = 0, verseId = psalm23Verse1))
        composeRule
            .onNodeWithTag("reader-verse-$psalm23Verse1")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "selected"))
        composeRule
            .onNodeWithTag("reader-verse-$psalm23Verse2")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "not selected"))
        // The superscription shares the one interaction modifier, so it speaks its state too.
        composeRule
            .onNodeWithTag("reader-title-$psalm23Title")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "not selected"))
        // The tap label changes to match what a tap now does (toggle, not open the menu).
        composeRule
            .onNodeWithTag("reader-verse-$psalm23Verse2")
            .assert(hasClickLabel("Change selection"))
            .assert(hasLongClickLabel("Select verses"))
    }

    @Test
    fun `picker book rows and chapter cells meet 48dp touch targets`() {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                BookChapterPicker(onChapterSelected = { _, _ -> })
            }
        }
        composeRule.onNodeWithTag("picker-book-1").assertTouchTargetAtLeast(48.dp)
        composeRule.onNodeWithTag("picker-book-1").performClick()
        for (chapter in listOf(1, 2, 3)) {
            composeRule.onNodeWithTag("picker-chapter-$chapter").assertTouchTargetAtLeast(48.dp)
        }
    }

    @Test
    fun `update Restart snackbar action meets 48dp touch target and is spoken`() {
        // S-L (D-L-4): the in-app-update Restart affordance is an authored interactive control —
        // it must meet the 48dp touch target and carry spoken text (never an unlabeled icon).
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                val host = remember { SnackbarHostState() }
                UpdateRestartSnackbarEffect(UpdatePhase.ReadyToRestart, host, onRestart = {})
                SnackbarHost(host)
            }
        }
        composeRule.onNodeWithText("Restart").assertTouchTargetAtLeast(48.dp)
    }

    private fun hasTextContaining(substring: String): SemanticsMatcher =
        SemanticsMatcher("has text containing '$substring'") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.contains(substring) } == true
        }
}

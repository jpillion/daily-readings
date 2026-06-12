package com.jpillion.dailyreadingplanner.ui.day

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpillion.dailyreadingplanner.R
import com.jpillion.dailyreadingplanner.domain.model.DayCompletion
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.ReadingStatus
import com.jpillion.dailyreadingplanner.ui.browser.launchReadingDestination
import com.jpillion.dailyreadingplanner.ui.datepicker.DayDatePickerDialog
import com.jpillion.dailyreadingplanner.ui.stats.StatsContent
import com.jpillion.dailyreadingplanner.ui.stats.StatsPanelUiState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Pager geometry (D-S5-4): a bounded window of [PAGE_COUNT] real calendar days with "today"
 * (pinned at ViewModel creation) at the center page. Swiping steps actual dates, so crossing
 * Dec 31 lands on Jan 1 of the *next* year (D-S5-3) — ±[DAY_WINDOW] days ≈ 27 years each way.
 */
internal const val DAY_WINDOW = 10_000
internal const val TODAY_PAGE = DAY_WINDOW
internal const val PAGE_COUNT = 2 * DAY_WINDOW + 1

internal fun dateForPage(
    today: LocalDate,
    page: Int,
): LocalDate = today.plusDays((page - TODAY_PAGE).toLong())

internal fun pageForDate(
    today: LocalDate,
    date: LocalDate,
): Int = (TODAY_PAGE + (date.toEpochDay() - today.toEpochDay())).toInt().coerceIn(0, PAGE_COUNT - 1)

/** Stateful entry point: collects the ViewModel and owns the Custom-Tab side-effect (D-S4-2). */
@Composable
fun DayReadingsRoute(
    onOpenSettings: () -> Unit,
    viewModel: DayReadingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.openDestinationEvents.collect { destination ->
            launchReadingDestination(context, destination)
        }
    }
    val statsPanel by viewModel.statsPanel.collectAsStateWithLifecycle()
    DayReadingsPagerScreen(
        today = viewModel.today,
        uiStateFor = viewModel::uiStateFor,
        monthCompletionFor = viewModel::monthCompletionFor,
        statsPanel = statsPanel,
        onToggleReading = viewModel::onToggleReading,
        onMarkWholeDay = viewModel::onMarkWholeDay,
        onReadingTapped = viewModel::onReadingTapped,
        onRetry = viewModel::onRetry,
        onOpenSettings = onOpenSettings,
    )
}

/**
 * The day-readings screen (FR-1, FR-5, FR-12): a [HorizontalPager] of single-day pages, each
 * collecting its own date's state via [uiStateFor] so neighboring pages are always correct
 * mid-swipe (D-S5-1). The top bar offers "jump to today" whenever the displayed day isn't
 * today, and a calendar action that opens the date picker dialog (D-S5-2).
 *
 * Stateless apart from pager/dialog UI state — testable without Hilt or a ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayReadingsPagerScreen(
    today: LocalDate,
    uiStateFor: (LocalDate) -> StateFlow<DayUiState>,
    monthCompletionFor: (YearMonth) -> StateFlow<Map<LocalDate, DayCompletion>>,
    statsPanel: StatsPanelUiState?,
    onToggleReading: (LocalDate, ReadingStatus) -> Unit,
    onMarkWholeDay: (LocalDate, Boolean) -> Unit,
    onReadingTapped: (Portion) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(initialPage = TODAY_PAGE) { PAGE_COUNT }
    val scope = rememberCoroutineScope()
    val currentDate = dateForPage(today, pagerState.currentPage)
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    // D-S16-1: a single-line title — "Today – June 12" on today, otherwise just
                    // the date (year shown only when it differs from today's, i.e. after
                    // swiping across Dec 31 → Jan 1). maxLines = 1 guarantees one line.
                    Text(
                        text =
                            if (currentDate == today) {
                                stringResource(R.string.title_today_date, formatMonthDay(currentDate))
                            } else {
                                formatDayDate(currentDate, today.year)
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    if (currentDate != today) {
                        TextButton(
                            onClick = { scope.launch { pagerState.animateScrollToPage(TODAY_PAGE) } },
                            modifier = Modifier.testTag("jump-to-today"),
                        ) { Text(text = stringResource(R.string.jump_to_today)) }
                    }
                    IconButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.testTag("open-date-picker"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DateRange,
                            contentDescription = stringResource(R.string.open_date_picker),
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("open-settings"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.open_settings),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        // D-S15-4: the stats panel renders ONCE below the pager (year-level stats are the
        // same for every displayed day). The panel is measured first, capped at 45% of the
        // available height and internally scrollable, so the readings always keep the
        // majority of the screen — on any screen size and font scale.
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            val statsMaxHeight = maxHeight * 0.45f
            Column(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("day-pager"),
                    key = { it },
                ) { page ->
                    val date = dateForPage(today, page)
                    val state by uiStateFor(date).collectAsStateWithLifecycle()
                    DayContent(
                        state = state,
                        onToggleReading = { reading -> onToggleReading(date, reading) },
                        onMarkWholeDay = {
                            (state as? DayUiState.Scheduled)?.let { onMarkWholeDay(date, it.dayComplete) }
                        },
                        onReadingTapped = onReadingTapped,
                        onRetry = onRetry,
                    )
                }
                if (statsPanel != null) {
                    HorizontalDivider()
                    StatsContent(
                        stats = statsPanel.stats,
                        strips = statsPanel.strips,
                        showStreaks = statsPanel.showStreaks,
                        modifier =
                            Modifier
                                .heightIn(max = statsMaxHeight)
                                .verticalScroll(rememberScrollState())
                                .testTag("stats-panel"),
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        DayDatePickerDialog(
            year = today.year,
            today = today,
            initialDate = if (currentDate.year == today.year) currentDate else today,
            completionFor = monthCompletionFor,
            onConfirm = { picked ->
                showDatePicker = false
                scope.launch { pagerState.scrollToPage(pageForDate(today, picked)) }
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

/** D-S16-1: "June 12" — the month-day half of the "Today – …" title. */
internal fun formatMonthDay(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("MMMM d"))

/**
 * D-S16-1: the title for a non-today page — "Friday, June 13", with the year appended only
 * when it differs from [todayYear] (the pager crosses Dec 31 → Jan 1, D-S5-3).
 */
internal fun formatDayDate(
    date: LocalDate,
    todayYear: Int,
): String {
    val base = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
    return if (date.year == todayYear) base else "$base, ${date.year}"
}

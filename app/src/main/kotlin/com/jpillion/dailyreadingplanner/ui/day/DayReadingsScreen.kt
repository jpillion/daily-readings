package com.jpillion.dailyreadingplanner.ui.day

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpillion.dailyreadingplanner.R
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.ReadingStatus
import com.jpillion.dailyreadingplanner.ui.browser.launchCustomTab
import com.jpillion.dailyreadingplanner.ui.datepicker.DayDatePickerDialog
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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
        viewModel.openUrlEvents.collect { url -> launchCustomTab(context, url) }
    }
    DayReadingsPagerScreen(
        today = viewModel.today,
        uiStateFor = viewModel::uiStateFor,
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
                    Column {
                        Text(
                            text =
                                if (currentDate == today) {
                                    stringResource(R.string.today_title)
                                } else {
                                    stringResource(R.string.readings_title)
                                },
                        )
                        Text(
                            text = formatDate(currentDate),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
        HorizontalPager(
            state = pagerState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
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
    }

    if (showDatePicker) {
        DayDatePickerDialog(
            year = today.year,
            initialDate = if (currentDate.year == today.year) currentDate else today,
            onConfirm = { picked ->
                showDatePicker = false
                scope.launch { pagerState.scrollToPage(pageForDate(today, picked)) }
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

private fun formatDate(date: LocalDate): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))

package com.jpillion.dailyreadingplanner.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpillion.dailyreadingplanner.R
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.ReadingStatus
import com.jpillion.dailyreadingplanner.ui.browser.launchCustomTab
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Stateful entry point: collects the ViewModel and owns the Custom-Tab side-effect (D-S4-2). */
@Composable
fun TodayRoute(viewModel: TodayViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.openUrlEvents.collect { url -> launchCustomTab(context, url) }
    }
    TodayScreen(
        state = state,
        onToggleReading = viewModel::onToggleReading,
        onMarkWholeDay = viewModel::onMarkWholeDay,
        onReadingTapped = viewModel::onReadingTapped,
        onRetry = viewModel::onRetry,
    )
}

/** Stateless screen over [TodayUiState] — directly testable without Hilt or a ViewModel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    state: TodayUiState,
    onToggleReading: (ReadingStatus) -> Unit,
    onMarkWholeDay: () -> Unit,
    onReadingTapped: (Portion) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = stringResource(R.string.today_title))
                        Text(
                            text = formatDate(state.dateOrNull()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            when (state) {
                is TodayUiState.Loading -> LoadingContent()
                is TodayUiState.Scheduled ->
                    ScheduledContent(
                        state = state,
                        onToggleReading = onToggleReading,
                        onMarkWholeDay = onMarkWholeDay,
                        onReadingTapped = onReadingTapped,
                    )
                is TodayUiState.NoScheduledReadings -> NoReadingsContent()
                is TodayUiState.LoadFailed -> LoadFailedContent(onRetry = onRetry)
            }
        }
    }
}

private fun TodayUiState.dateOrNull(): LocalDate? =
    when (this) {
        is TodayUiState.Loading -> null
        is TodayUiState.Scheduled -> date
        is TodayUiState.NoScheduledReadings -> date
        is TodayUiState.LoadFailed -> date
    }

private fun formatDate(date: LocalDate?): String =
    date?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)).orEmpty()

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.testTag("loading"))
    }
}

@Composable
private fun ScheduledContent(
    state: TodayUiState.Scheduled,
    onToggleReading: (ReadingStatus) -> Unit,
    onMarkWholeDay: () -> Unit,
    onReadingTapped: (Portion) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.readings.forEach { reading ->
            ReadingCard(
                reading = reading,
                onToggleReading = onToggleReading,
                onReadingTapped = onReadingTapped,
            )
        }
        Spacer(modifier = Modifier.padding(top = 4.dp))
        CompletionIndicator(state = state)
        WholeDayButton(dayComplete = state.dayComplete, onMarkWholeDay = onMarkWholeDay)
    }
}

@Composable
private fun ReadingCard(
    reading: ReadingStatus,
    onToggleReading: (ReadingStatus) -> Unit,
    onReadingTapped: (Portion) -> Unit,
) {
    val portion = reading.portion
    val referenceText = ReadingFormatter.format(portion)
    Card(
        onClick = { onReadingTapped(portion) },
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("reading-${portion.stream.number}"),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (reading.isRead) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ReadingFormatter.streamTitle(portion.stream),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = referenceText,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.reading_open_hint, referenceText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Checkbox(
                checked = reading.isRead,
                onCheckedChange = { onToggleReading(reading) },
                modifier =
                    Modifier
                        // G-A11Y: keep the toggle a ≥48dp target.
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("toggle-${portion.stream.number}"),
            )
        }
    }
}

@Composable
private fun CompletionIndicator(state: TodayUiState.Scheduled) {
    val readCount = state.readings.count { it.isRead }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.dayComplete) {
            Text(
                text = stringResource(R.string.day_complete),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                text = stringResource(R.string.day_progress, readCount, state.readings.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WholeDayButton(
    dayComplete: Boolean,
    onMarkWholeDay: () -> Unit,
) {
    val label =
        if (dayComplete) {
            stringResource(R.string.unmark_whole_day)
        } else {
            stringResource(R.string.mark_whole_day_done)
        }
    if (dayComplete) {
        OutlinedButton(
            onClick = onMarkWholeDay,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("whole-day-button"),
        ) { Text(text = label) }
    } else {
        Button(
            onClick = onMarkWholeDay,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("whole-day-button"),
        ) { Text(text = label) }
    }
}

@Composable
private fun NoReadingsContent() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.no_scheduled_readings_feb29),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.padding(top = 8.dp))
        Text(
            text = stringResource(R.string.enjoy_the_rest_day),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadFailedContent(onRetry: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.load_failed_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.padding(top = 8.dp))
        Text(
            text = stringResource(R.string.load_failed_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.padding(top = 16.dp))
        Button(onClick = onRetry, modifier = Modifier.testTag("retry-button")) {
            Text(text = stringResource(R.string.retry))
        }
    }
}

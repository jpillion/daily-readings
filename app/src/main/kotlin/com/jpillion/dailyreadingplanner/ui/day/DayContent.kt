package com.jpillion.dailyreadingplanner.ui.day

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jpillion.dailyreadingplanner.R
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.ReadingStatus

/**
 * Stateless content for one day's readings — directly testable without Hilt or a ViewModel.
 * Sprint 5 (D-S5-1): this is Sprint 4's TodayScreen body, generalized so each pager page
 * renders one [DayUiState]; the caller binds the page's date into the callbacks. The Scaffold
 * and top bar live above the pager in [DayReadingsPagerScreen].
 */
@Composable
fun DayContent(
    state: DayUiState,
    onToggleReading: (ReadingStatus) -> Unit,
    onMarkWholeDay: () -> Unit,
    onReadingTapped: (Portion) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (state) {
            is DayUiState.Loading -> LoadingContent()
            is DayUiState.Scheduled ->
                ScheduledContent(
                    state = state,
                    onToggleReading = onToggleReading,
                    onMarkWholeDay = onMarkWholeDay,
                    onReadingTapped = onReadingTapped,
                )
            is DayUiState.NoScheduledReadings -> NoReadingsContent()
            is DayUiState.LoadFailed -> LoadFailedContent(onRetry = onRetry)
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.testTag("loading"))
    }
}

@Composable
private fun ScheduledContent(
    state: DayUiState.Scheduled,
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
private fun CompletionIndicator(state: DayUiState.Scheduled) {
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

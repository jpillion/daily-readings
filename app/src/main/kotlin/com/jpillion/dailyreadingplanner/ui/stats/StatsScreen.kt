package com.jpillion.dailyreadingplanner.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpillion.dailyreadingplanner.R
import com.jpillion.dailyreadingplanner.domain.model.ReadingStats
import com.jpillion.dailyreadingplanner.domain.model.Stream
import com.jpillion.dailyreadingplanner.ui.day.ReadingFormatter
import java.text.NumberFormat

/** Stateful entry point for the pushed `stats` route (S11, FR-15). */
@Composable
fun StatsRoute(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    StatsScreen(stats = stats, onBack = onBack)
}

/**
 * The read-only Stats screen (PRD §13.1): exactly four stat groups — current streak,
 * longest streak, year progress, per-stream progress — in plain M3 typography with quiet
 * standard progress indicators. Sober by contract (§13.0/FR-16): no celebration, no
 * missed-day callouts, no red; missed days exist only as the gap between n and the
 * denominator. Stateless over a nullable [ReadingStats] (null = first frame, renders
 * nothing) — testable without Hilt or a ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    stats: ReadingStats?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("stats-back"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (stats == null) return@Scaffold
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StreakRow(
                label = stringResource(R.string.stats_current_streak),
                days = stats.currentStreakDays,
                tag = "stats-current-streak",
            )
            StreakRow(
                label = stringResource(R.string.stats_longest_streak),
                days = stats.longestStreakDays,
                tag = "stats-longest-streak",
            )
            Spacer(modifier = Modifier.height(12.dp))
            YearGroup(stats = stats)
            Spacer(modifier = Modifier.height(12.dp))
            StreamGroup(stats = stats)
        }
    }
}

/** One streak line: quiet label left, plain number right (groups 1 and 2). */
@Composable
private fun StreakRow(
    label: String,
    days: Int,
    tag: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(tag)
                .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = pluralStringResource(R.plurals.stats_streak_days, days, days),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

/** Group 3: % of the year's 1,095 readings — full-year denominator, floor rounding (D-S11-4). */
@Composable
private fun YearGroup(stats: ReadingStats) {
    val numberFormat = NumberFormat.getIntegerInstance()
    val percent = stats.yearReadCount * 100 / ReadingStats.YEAR_TOTAL_READINGS
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("stats-year")
                .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.stats_year_section),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = stringResource(R.string.stats_percent, percent),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text =
                    stringResource(
                        R.string.stats_year_count,
                        numberFormat.format(stats.yearReadCount),
                        numberFormat.format(ReadingStats.YEAR_TOTAL_READINGS),
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { stats.yearReadCount.toFloat() / ReadingStats.YEAR_TOTAL_READINGS },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Group 4: one quiet bar per stream, "n of 365" each. */
@Composable
private fun StreamGroup(stats: ReadingStats) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.stats_stream_section),
            style = MaterialTheme.typography.titleMedium,
        )
        Stream.entries.forEach { stream ->
            val count = stats.streamReadCounts[stream] ?: 0
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("stats-stream-${stream.number}")
                        .semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ReadingFormatter.streamTitle(stream),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text =
                            stringResource(
                                R.string.stats_stream_count,
                                count,
                                ReadingStats.STREAM_TOTAL_DAYS,
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LinearProgressIndicator(
                    progress = { count.toFloat() / ReadingStats.STREAM_TOTAL_DAYS },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

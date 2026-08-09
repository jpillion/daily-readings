package com.jpillion.dailyreadingplanner.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jpillion.dailyreadingplanner.R
import com.jpillion.dailyreadingplanner.domain.model.ReadingStats
import com.jpillion.dailyreadingplanner.domain.model.StripDayState
import com.jpillion.dailyreadingplanner.domain.model.YearStrips
import com.jpillion.dailyreadingplanner.platform.DateTextFormatter
import com.jpillion.dailyreadingplanner.platform.rememberDateTextFormatter

/**
 * What the main-screen stats panel renders (S15, D-S15-4): the live stats + the streak gate,
 * plus the year-strip day states (S17).
 */
data class StatsPanelUiState(
    val stats: ReadingStats,
    val showStreaks: Boolean,
    val strips: YearStrips,
)

/**
 * The stats content (PRD §13.1), S15 (D-S15-4): rendered inline below the day pager instead
 * of behind a pushed route — the stats are year-level, identical whichever day is displayed,
 * so they render once under the pager, not per page. Sober by contract (§13.0/FR-16, pinned
 * in StatsContentTest), amended by D-S17-1: the year strips may show red segments — red is
 * information, not commentary — but no COPY ever calls out missing/failure, on screen or in
 * speech ("not read", never "missed"). Further amended by D-S20-1 (owner): the strip legend's
 * two labels — "Missed" / "Completed", the owner's own wording — are the SOLE exemption.
 *
 * D-S15-5: [showStreaks] off hides the two streak rows (display only — the underlying
 * derivation is untouched); year and per-stream progress always show.
 */
@Composable
fun StatsContent(
    stats: ReadingStats,
    strips: YearStrips,
    showStreaks: Boolean,
    modifier: Modifier = Modifier,
    // p1-01: the locale's grouping separator ("1,095") comes from the platform seam. Defaulted
    // so every existing caller and the "438 of 1,095 readings" pins are unchanged.
    formatter: DateTextFormatter = rememberDateTextFormatter(),
) {
    // S18 (owner): deliberate density — readings + this whole panel must fit one screen on
    // a ~411x915dp device at default font. Section labels merged into value rows, gaps and
    // insets one step tighter; the vertical budget table lives in the S18 handoff.
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // S20 (owner): a compact heading names what the panel shows — labelLarge, one line,
        // ~20dp of the S18 budget (updated table in the S20 handoff).
        Text(
            text = stringResource(R.string.stats_panel_heading),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .testTag("stats-heading")
                    .semantics { heading() },
        )
        if (showStreaks) {
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
        }
        YearGroup(stats = stats, strips = strips, formatter = formatter)
        StreamGroup(stats = stats, strips = strips)
        StripLegend()
    }
}

/**
 * S20 (owner): the strip color key — red = Missed, green = Completed, the owner's own words
 * (D-S20-1, a deliberate owner amendment narrowing the D-S17-1/D-S17-3 copy ban to permit
 * exactly these two legend labels; all other guilt copy stays banned, on screen and in
 * speech). Swatches come from the same [StripColors] seam the strips paint with, so the
 * legend can never disagree with the strips. One merged-semantics row: TalkBack reads
 * "Missed, Completed" after the strip summaries; the swatches themselves announce nothing.
 */
@Composable
private fun StripLegend() {
    val colors = defaultStripColors()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("stats-legend")
                .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendEntry(swatch = colors.missed, label = stringResource(R.string.stats_legend_missed))
        LegendEntry(swatch = colors.read, label = stringResource(R.string.stats_legend_completed))
    }
}

/** One legend entry: a 10dp color swatch (purely decorative — no semantics) + its label. */
@Composable
private fun LegendEntry(
    swatch: Color,
    label: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(swatch),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/**
 * Group 3: % of the year's 1,095 readings — full-year denominator, floor rounding (D-S11-4).
 * The bar is gone (S17): the three stream strips stack into a compact year heat-strip, one
 * combined spoken summary (color-only pixels, D-S17-3 wording). S18: the "This year" label,
 * the percent, and the count share ONE row (the old label row + headline row cost ~40dp).
 */
@Composable
private fun YearGroup(
    stats: ReadingStats,
    strips: YearStrips,
    formatter: DateTextFormatter,
) {
    val percent = stats.yearReadCount * 100 / stats.yearTotalReadings
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("stats-year")
                .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = stringResource(R.string.stats_year_section),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.stats_percent, percent),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text =
                    stringResource(
                        R.string.stats_year_count,
                        formatter.integer(stats.yearReadCount),
                        formatter.integer(stats.yearTotalReadings),
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        val allStates = strips.streams.flatMap { strips.dayStates[it.number].orEmpty() }
        val yearSummary =
            stringResource(
                R.string.strip_year_summary,
                allStates.count { it == StripDayState.READ },
                allStates.count { it == StripDayState.MISSED },
                allStates.count { it == StripDayState.NEUTRAL },
            )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("strip-year")
                    .clearAndSetSemantics { contentDescription = yearSummary },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val colors = defaultStripColors()
            strips.streams.forEach { stream ->
                YearStrip(
                    states = strips.dayStates[stream.number].orEmpty(),
                    todayIndex = strips.todayIndex,
                    colors = colors,
                    height = 6.dp,
                )
            }
        }
    }
}

/** Group 4: one year strip per stream (S17, was a quiet bar), "n of 365" each. */
@Composable
private fun StreamGroup(
    stats: ReadingStats,
    strips: YearStrips,
) {
    val colors = defaultStripColors()
    // S18: no "By stream" section header — the stream names are self-describing and the
    // header row cost ~36dp (deliberate removal, pinned by absence in StatsContentTest).
    Column(
        modifier = Modifier.fillMaxWidth(),
        // One-screen-fit trim (owner): stream-group gap 10→8dp.
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // D-ALT-9/22: one row per ACTIVE-plan stream (in descriptor order), keyed by stream
        // number; the title is plan data from the descriptor, the denominator is an instance field.
        stats.streams.forEach { stream ->
            val count = stats.streamReadCounts[stream.number] ?: 0
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
                        text = stream.title,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text =
                            stringResource(
                                R.string.stats_stream_count,
                                count,
                                stats.streamTotalDays,
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val states = strips.dayStates[stream.number].orEmpty()
                val summary =
                    stringResource(
                        R.string.strip_stream_summary,
                        stream.title,
                        states.count { it == StripDayState.READ },
                        states.count { it == StripDayState.MISSED },
                        states.count { it == StripDayState.NEUTRAL },
                    )
                YearStrip(
                    states = states,
                    todayIndex = strips.todayIndex,
                    colors = colors,
                    // One-screen-fit trim (owner): per-stream strip height 10→8dp.
                    height = 8.dp,
                    modifier =
                        Modifier
                            .testTag("strip-stream-${stream.number}")
                            .clearAndSetSemantics { contentDescription = summary },
                )
            }
        }
    }
}

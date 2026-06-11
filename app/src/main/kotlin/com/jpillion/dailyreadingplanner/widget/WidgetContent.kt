package com.jpillion.dailyreadingplanner.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.jpillion.dailyreadingplanner.MainActivity
import com.jpillion.dailyreadingplanner.R
import com.jpillion.dailyreadingplanner.domain.model.DayReadings
import com.jpillion.dailyreadingplanner.domain.model.ReadingStatus
import com.jpillion.dailyreadingplanner.ui.day.ReadingFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the widget renders: today's resolved readings, or the degrade-don't-crash fallback. */
sealed interface TodayWidgetState {
    data class Loaded(
        val day: DayReadings,
    ) : TodayWidgetState

    data object LoadFailed : TodayWidgetState
}

/** The four responsive breakpoints (S8 D-S8-1, refined S9 D-S9-2). */
enum class WidgetLayout { TINY, SMALL, MEDIUM, LARGE }

// Launcher cells are ~57dp+ wide and ~48dp+ tall: 1 column always exceeds TINY/SMALL's
// 57dp, 2 columns MEDIUM's 130dp on common grids, 3 columns LARGE's 203dp. One row clears
// TINY's 48dp but stays under SMALL's 102dp; two rows clear SMALL.
internal val TINY_SIZE = DpSize(57.dp, 48.dp)
internal val SMALL_SIZE = DpSize(57.dp, 102.dp)
internal val MEDIUM_SIZE = DpSize(130.dp, 102.dp)
internal val LARGE_SIZE = DpSize(203.dp, 102.dp)

/**
 * Pure breakpoint chooser: the widest layout whose minimum width fits; below MEDIUM the
 * available *height* splits 1x2 (date header fits) from 1x1 (readings only).
 */
internal fun layoutFor(size: DpSize): WidgetLayout =
    when {
        size.width >= LARGE_SIZE.width -> WidgetLayout.LARGE
        size.width >= MEDIUM_SIZE.width -> WidgetLayout.MEDIUM
        size.height >= SMALL_SIZE.height -> WidgetLayout.SMALL
        else -> WidgetLayout.TINY
    }

private val HeaderDateFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
private val ShortDateFormat = DateTimeFormatter.ofPattern("MMM d", Locale.US)

/**
 * Widget UI (ESpec §7 + D-S8-1 + S9 owner feedback): read-only, one responsive surface
 * that **lists the three readings at every size** — completion is secondary, never the
 * focus (D-S9-2):
 * - LARGE (~3x2): three rows of stream title + collapsed reference (the same
 *   [ReadingFormatter] the app uses) with read/unread marks and an "all done" badge.
 * - MEDIUM (~2x2): drops the stream titles; marks + full references.
 * - SMALL (~1x2): date header + three abbreviated references ("Gen 1–2", D-S9-1) with marks.
 * - TINY (~1x1): just the three abbreviated references with marks.
 * Every layout keeps the Feb 29 / load-failure states, the single whole-surface tap target
 * into the app (FR-8), and full spoken descriptions (canonical book names, never glyphs or
 * abbreviations alone); marking happens in-app (toggle-from-widget is a V2 candidate).
 */
@Composable
fun WidgetContent(state: TodayWidgetState) {
    val layout = layoutFor(LocalSize.current)
    val padding =
        when (layout) {
            WidgetLayout.TINY -> 6.dp
            WidgetLayout.SMALL -> 8.dp
            else -> 12.dp
        }
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(padding)
                .clickable(actionStartActivity<MainActivity>())
                .semantics { testTag = "widget-root" },
    ) {
        when (state) {
            is TodayWidgetState.LoadFailed -> LoadFailed(layout)
            is TodayWidgetState.Loaded ->
                when (val day = state.day) {
                    is DayReadings.NoScheduledReadings -> NoReadings(day.date, layout)
                    is DayReadings.Scheduled ->
                        when (layout) {
                            WidgetLayout.LARGE -> Readings(day)
                            WidgetLayout.MEDIUM -> CompactReadings(day)
                            WidgetLayout.SMALL -> AbbreviatedReadings(day, showDate = true)
                            WidgetLayout.TINY -> AbbreviatedReadings(day, showDate = false)
                        }
                }
        }
    }
}

@Composable
private fun Readings(day: DayReadings.Scheduled) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Header(day.date)
        Spacer(modifier = GlanceModifier.defaultWeight())
        if (day.dayComplete) {
            Text(
                text = LocalContext.current.getString(R.string.day_complete),
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                modifier = GlanceModifier.semantics { testTag = "widget-day-complete" },
            )
        }
    }
    Spacer(modifier = GlanceModifier.height(8.dp))
    day.readings.forEachIndexed { index, reading ->
        if (index > 0) Spacer(modifier = GlanceModifier.height(6.dp))
        ReadingRow(reading, showStreamTitle = true, abbreviated = false)
    }
}

@Composable
private fun CompactReadings(day: DayReadings.Scheduled) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Header(day.date)
        Spacer(modifier = GlanceModifier.defaultWeight())
        if (day.dayComplete) DayCompleteBadge(fontSize = 14)
    }
    Spacer(modifier = GlanceModifier.height(6.dp))
    day.readings.forEachIndexed { index, reading ->
        if (index > 0) Spacer(modifier = GlanceModifier.height(4.dp))
        ReadingRow(reading, showStreamTitle = false, abbreviated = false)
    }
}

/**
 * The width-constrained layouts (1x2 with the date, 1x1 without): the three readings are
 * always listed — abbreviated (D-S9-1) so they fit a single launcher cell — with their
 * read/unread marks; completion is only a small badge beside the date (1x2) or implicit in
 * the marks (1x1). Completion count is deliberately not rendered (S9 owner feedback).
 */
@Composable
private fun AbbreviatedReadings(
    day: DayReadings.Scheduled,
    showDate: Boolean,
) {
    if (showDate) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = day.date.format(ShortDateFormat),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.semantics { testTag = "widget-date" },
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            if (day.dayComplete) DayCompleteBadge(fontSize = 11)
        }
        Spacer(modifier = GlanceModifier.height(4.dp))
    }
    day.readings.forEachIndexed { index, reading ->
        if (index > 0) Spacer(modifier = GlanceModifier.height(2.dp))
        ReadingRow(reading, showStreamTitle = false, abbreviated = true)
    }
}

@Composable
private fun DayCompleteBadge(fontSize: Int) {
    val dayCompleteText = LocalContext.current.getString(R.string.day_complete)
    Text(
        // The badge collapses to a checkmark below 3x2; a11y still speaks the full state.
        text = "✓",
        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = fontSize.sp, fontWeight = FontWeight.Bold),
        modifier =
            GlanceModifier.semantics {
                testTag = "widget-day-complete"
                contentDescription = dayCompleteText
            },
    )
}

@Composable
private fun ReadingRow(
    reading: ReadingStatus,
    showStreamTitle: Boolean,
    abbreviated: Boolean,
) {
    val context = LocalContext.current
    val stateDescription =
        context.getString(
            if (reading.isRead) R.string.widget_reading_read else R.string.widget_reading_unread,
        )
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            // Read-only completion mark (ESpec §7); state is also exposed to a11y via the
            // row's contentDescription, since the glyph alone is not accessible.
            text = if (reading.isRead) "✓" else "○",
            style =
                TextStyle(
                    color = if (reading.isRead) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant,
                    fontSize = if (abbreviated) 11.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
            modifier =
                GlanceModifier.semantics {
                    testTag = if (reading.isRead) "widget-mark-read" else "widget-mark-unread"
                },
        )
        Spacer(modifier = GlanceModifier.width(if (abbreviated) 4.dp else 8.dp))
        Column(
            modifier =
                GlanceModifier.semantics {
                    // Always the full canonical reference — abbreviations are visual only.
                    contentDescription =
                        "${ReadingFormatter.streamTitle(reading.portion.stream)}, " +
                        "${ReadingFormatter.format(reading.portion)}, $stateDescription"
                },
        ) {
            if (showStreamTitle) {
                Text(
                    text = ReadingFormatter.streamTitle(reading.portion.stream),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                )
            }
            Text(
                text =
                    if (abbreviated) {
                        ReadingFormatter.formatAbbreviated(reading.portion)
                    } else {
                        ReadingFormatter.format(reading.portion)
                    },
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = if (abbreviated) 11.sp else 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun NoReadings(
    date: LocalDate,
    layout: WidgetLayout,
) {
    val noReadingsText = LocalContext.current.getString(R.string.no_scheduled_readings_feb29)
    when (layout) {
        WidgetLayout.TINY ->
            Column(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "—",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 20.sp),
                    modifier =
                        GlanceModifier.semantics {
                            testTag = "widget-no-readings"
                            contentDescription = noReadingsText
                        },
                )
            }
        WidgetLayout.SMALL ->
            Column(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = date.format(ShortDateFormat),
                    style =
                        TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    modifier = GlanceModifier.semantics { testTag = "widget-date" },
                )
                Spacer(modifier = GlanceModifier.height(6.dp))
                Text(
                    text = "—",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 22.sp),
                    modifier =
                        GlanceModifier.semantics {
                            testTag = "widget-no-readings"
                            contentDescription = noReadingsText
                        },
                )
            }
        else -> {
            Header(date)
            Spacer(modifier = GlanceModifier.height(8.dp))
            Text(
                text = noReadingsText,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
                modifier = GlanceModifier.semantics { testTag = "widget-no-readings" },
            )
        }
    }
}

@Composable
private fun LoadFailed(layout: WidgetLayout) {
    if (layout == WidgetLayout.SMALL || layout == WidgetLayout.TINY) {
        val loadFailedText = LocalContext.current.getString(R.string.widget_load_failed)
        Text(
            text = "!",
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold),
            modifier =
                GlanceModifier.semantics {
                    testTag = "widget-error"
                    contentDescription = loadFailedText
                },
        )
    } else {
        Text(
            text = LocalContext.current.getString(R.string.widget_load_failed),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
            modifier = GlanceModifier.semantics { testTag = "widget-error" },
        )
    }
}

@Composable
private fun Header(date: LocalDate) {
    Text(
        text = date.format(HeaderDateFormat),
        style =
            TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            ),
        modifier = GlanceModifier.semantics { testTag = "widget-date" },
    )
}

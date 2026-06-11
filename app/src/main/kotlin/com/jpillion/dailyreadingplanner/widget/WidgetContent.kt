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

/** The three responsive breakpoints (S8, D-S8-1), keyed by available width. */
enum class WidgetLayout { SMALL, MEDIUM, LARGE }

// Launcher cells are ~57dp+ wide: 1 column always exceeds SMALL, 2 columns MEDIUM's
// 130dp on common grids, 3 columns LARGE's 203dp. Height stays 2 rows across all three.
internal val SMALL_SIZE = DpSize(57.dp, 102.dp)
internal val MEDIUM_SIZE = DpSize(130.dp, 102.dp)
internal val LARGE_SIZE = DpSize(203.dp, 102.dp)

/** Pure breakpoint chooser: the widest layout whose minimum width fits. */
internal fun layoutFor(size: DpSize): WidgetLayout =
    when {
        size.width >= LARGE_SIZE.width -> WidgetLayout.LARGE
        size.width >= MEDIUM_SIZE.width -> WidgetLayout.MEDIUM
        else -> WidgetLayout.SMALL
    }

private val HeaderDateFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
private val ShortDateFormat = DateTimeFormatter.ofPattern("MMM d", Locale.US)

/**
 * Widget UI (ESpec §7 + S8 D-S8-1): read-only, one responsive surface.
 * - LARGE (~3x2): three rows of stream title + collapsed reference (the same
 *   [ReadingFormatter] the app uses) with read/unread marks and an "all done" badge.
 * - MEDIUM (~2x2): drops the stream titles; marks + references only.
 * - SMALL (~1x2): date plus an "n/3" completion summary — completion state at a glance.
 * Every layout keeps the Feb 29 / load-failure states and the single whole-surface tap
 * target into the app (FR-8); marking happens in-app (toggle-from-widget is a V2 candidate).
 */
@Composable
fun WidgetContent(state: TodayWidgetState) {
    val layout = layoutFor(LocalSize.current)
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(if (layout == WidgetLayout.SMALL) 8.dp else 12.dp)
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
                            WidgetLayout.SMALL -> MinimalReadings(day)
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
        ReadingRow(reading, showStreamTitle = true)
    }
}

@Composable
private fun CompactReadings(day: DayReadings.Scheduled) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Header(day.date)
        Spacer(modifier = GlanceModifier.defaultWeight())
        if (day.dayComplete) {
            val dayCompleteText = LocalContext.current.getString(R.string.day_complete)
            Text(
                // The badge collapses to a checkmark at 2x2; a11y still speaks the full state.
                text = "✓",
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                modifier =
                    GlanceModifier.semantics {
                        testTag = "widget-day-complete"
                        contentDescription = dayCompleteText
                    },
            )
        }
    }
    Spacer(modifier = GlanceModifier.height(6.dp))
    day.readings.forEachIndexed { index, reading ->
        if (index > 0) Spacer(modifier = GlanceModifier.height(4.dp))
        ReadingRow(reading, showStreamTitle = false)
    }
}

@Composable
private fun MinimalReadings(day: DayReadings.Scheduled) {
    val readCount = day.readings.count { it.isRead }
    val total = day.readings.size
    val progressText = LocalContext.current.getString(R.string.day_progress, readCount, total)
    val dayCompleteText = LocalContext.current.getString(R.string.day_complete)
    Column(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = day.date.format(ShortDateFormat),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.semantics { testTag = "widget-date" },
        )
        Spacer(modifier = GlanceModifier.height(6.dp))
        Text(
            text = "$readCount/$total",
            style =
                TextStyle(
                    color = if (day.dayComplete) GlanceTheme.colors.primary else GlanceTheme.colors.onSurface,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                ),
            modifier =
                GlanceModifier.semantics {
                    testTag = "widget-count"
                    contentDescription = progressText
                },
        )
        if (day.dayComplete) {
            Text(
                text = "✓",
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                modifier =
                    GlanceModifier.semantics {
                        testTag = "widget-day-complete"
                        contentDescription = dayCompleteText
                    },
            )
        }
    }
}

@Composable
private fun ReadingRow(
    reading: ReadingStatus,
    showStreamTitle: Boolean,
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
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
            modifier =
                GlanceModifier.semantics {
                    testTag = if (reading.isRead) "widget-mark-read" else "widget-mark-unread"
                },
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Column(
            modifier =
                GlanceModifier.semantics {
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
                text = ReadingFormatter.format(reading.portion),
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 14.sp,
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
    if (layout == WidgetLayout.SMALL) {
        val noReadingsText = LocalContext.current.getString(R.string.no_scheduled_readings_feb29)
        Column(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.format(ShortDateFormat),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold),
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
    } else {
        Header(date)
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            text = LocalContext.current.getString(R.string.no_scheduled_readings_feb29),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
            modifier = GlanceModifier.semantics { testTag = "widget-no-readings" },
        )
    }
}

@Composable
private fun LoadFailed(layout: WidgetLayout) {
    if (layout == WidgetLayout.SMALL) {
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

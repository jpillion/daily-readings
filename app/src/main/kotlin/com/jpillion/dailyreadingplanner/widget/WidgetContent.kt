package com.jpillion.dailyreadingplanner.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
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

private val HeaderDateFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)

/**
 * Widget UI (ESpec §7): read-only — three rows of stream title + collapsed reference (the
 * same [ReadingFormatter] the app uses) with a read/unread mark, an "all done" badge, and
 * the Feb 29 / load-failure states. The whole surface is one tap target that opens the app
 * on the Today route (FR-8); marking happens in-app (toggle-from-widget is a V2 candidate).
 */
@Composable
fun WidgetContent(state: TodayWidgetState) {
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>())
                .semantics { testTag = "widget-root" },
    ) {
        when (state) {
            is TodayWidgetState.LoadFailed -> LoadFailed()
            is TodayWidgetState.Loaded ->
                when (val day = state.day) {
                    is DayReadings.NoScheduledReadings -> NoReadings(day.date)
                    is DayReadings.Scheduled -> Readings(day)
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
        ReadingRow(reading)
    }
}

@Composable
private fun ReadingRow(reading: ReadingStatus) {
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
            Text(
                text = ReadingFormatter.streamTitle(reading.portion.stream),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
            )
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
private fun NoReadings(date: LocalDate) {
    Header(date)
    Spacer(modifier = GlanceModifier.height(8.dp))
    Text(
        text = LocalContext.current.getString(R.string.no_scheduled_readings_feb29),
        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
        modifier = GlanceModifier.semantics { testTag = "widget-no-readings" },
    )
}

@Composable
private fun LoadFailed() {
    Text(
        text = LocalContext.current.getString(R.string.widget_load_failed),
        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
        modifier = GlanceModifier.semantics { testTag = "widget-error" },
    )
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

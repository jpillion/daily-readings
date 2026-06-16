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
import androidx.glance.layout.ColumnScope
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

/** The four content tiers (S8 D-S8-1, refined S9 D-S9-2, redesigned S14 D-S14-2). */
enum class WidgetLayout { TINY, SMALL, MEDIUM, LARGE }

// Launcher cells are ~57dp+ wide and ~48dp+ tall: 1 column always exceeds TINY/SMALL's
// 57dp, 2 columns MEDIUM's 130dp on common grids, 3 columns LARGE's 203dp. One row clears
// the 48dp short heights but stays under 102dp; two rows clear 102dp.
internal val TINY_SIZE = DpSize(57.dp, 48.dp)
internal val SMALL_SIZE = DpSize(57.dp, 102.dp)
internal val MEDIUM_SIZE = DpSize(130.dp, 102.dp)
internal val LARGE_SIZE = DpSize(203.dp, 102.dp)

// S14 D-S14-2: wide-but-short size points. SizeMode.Responsive only picks a declared size
// that fits BOTH dimensions, so without these a 3x1-ish widget fell all the way to TINY —
// abbreviated rows lost in a wide box (the owner's "squeezed in" screenshot). Both map to
// the headerless MEDIUM rendering (full references; no date row to steal height).
internal val MEDIUM_SHORT_SIZE = DpSize(130.dp, 48.dp)
internal val WIDE_SHORT_SIZE = DpSize(203.dp, 48.dp)

/** Every size point the launcher may pick from (feeds [TodayWidget]'s SizeMode.Responsive). */
internal val RESPONSIVE_SIZES =
    setOf(TINY_SIZE, SMALL_SIZE, MEDIUM_SHORT_SIZE, MEDIUM_SIZE, WIDE_SHORT_SIZE, LARGE_SIZE)

/**
 * Pure tier chooser (S14 D-S14-2): LARGE needs room in BOTH axes; any other
 * MEDIUM-or-wider width gets full references (the date header is a separate height
 * decision, see [WidgetContent]); below that the height splits 1x2 from 1x1.
 */
internal fun layoutFor(size: DpSize): WidgetLayout =
    when {
        size.width >= LARGE_SIZE.width && size.height >= LARGE_SIZE.height -> WidgetLayout.LARGE
        size.width >= MEDIUM_SIZE.width -> WidgetLayout.MEDIUM
        size.height >= SMALL_SIZE.height -> WidgetLayout.SMALL
        else -> WidgetLayout.TINY
    }

/** Whether the layout has the vertical room for a dedicated date-header row. */
internal fun showsHeader(
    layout: WidgetLayout,
    size: DpSize,
): Boolean =
    when (layout) {
        WidgetLayout.LARGE -> true
        WidgetLayout.MEDIUM, WidgetLayout.SMALL -> size.height >= SMALL_SIZE.height
        WidgetLayout.TINY -> false
    }

private val HeaderDateFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
private val ShortDateFormat = DateTimeFormatter.ofPattern("MMM d", Locale.US)

/**
 * Per-tier type scale + insets (S14 D-S14-2, owner feedback): each size is a deliberate
 * design, not the same rows in a bigger box — type and padding grow with the card so
 * LARGE reads from a distance and TINY stays legible without crowding its edges.
 */
internal data class WidgetScale(
    val padding: Int,
    val dateSp: Int,
    val titleSp: Int,
    val referenceSp: Int,
    val markSp: Int,
    val markGap: Int,
)

internal fun scaleFor(layout: WidgetLayout): WidgetScale =
    when (layout) {
        WidgetLayout.LARGE ->
            WidgetScale(padding = 16, dateSp = 16, titleSp = 12, referenceSp = 17, markSp = 18, markGap = 10)
        WidgetLayout.MEDIUM ->
            WidgetScale(padding = 12, dateSp = 14, titleSp = 11, referenceSp = 15, markSp = 16, markGap = 8)
        WidgetLayout.SMALL ->
            WidgetScale(padding = 10, dateSp = 12, titleSp = 11, referenceSp = 13, markSp = 13, markGap = 6)
        WidgetLayout.TINY ->
            WidgetScale(padding = 8, dateSp = 11, titleSp = 11, referenceSp = 12, markSp = 12, markGap = 5)
    }

/**
 * Widget UI (ESpec §7 + D-S8-1 + S9/S14 owner feedback): read-only, one responsive surface
 * that **lists the three readings at every size** (D-S9-2) and **fills and balances the
 * card at every size** (D-S14-2 — the reading rows share the available height equally via
 * weights, so there is never a dead zone below the content):
 * - LARGE (3x2+): date header ("Thu, Jun 11") + "all done" badge, then three generous rows
 *   of stream title + full collapsed reference (the same [ReadingFormatter] the app uses).
 * - MEDIUM (2x2 / any wide-short): full references with marks; the date header appears
 *   only when the height affords it (a 3x1 widget spends every dp on the readings).
 * - SMALL (1x2): date header + three abbreviated references ("Gen 1–2", D-S9-1) with marks.
 * - TINY (1x1): just the three abbreviated references with marks.
 * Every tier keeps the Feb 29 / load-failure states (now centered both axes), the single
 * whole-surface tap target into the app (FR-8), and full spoken descriptions (canonical
 * book names, never glyphs or abbreviations alone); marking happens in-app (D-S7-4).
 */
@Composable
fun WidgetContent(state: TodayWidgetState) {
    val size = LocalSize.current
    val layout = layoutFor(size)
    val scale = scaleFor(layout)
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(scale.padding.dp)
                .clickable(actionStartActivity<MainActivity>())
                .semantics { testTag = "widget-root" },
    ) {
        when (state) {
            is TodayWidgetState.LoadFailed -> LoadFailed(layout)
            is TodayWidgetState.Loaded ->
                when (val day = state.day) {
                    is DayReadings.NoScheduledReadings ->
                        NoReadings(day.date, layout, showsHeader(layout, size))
                    is DayReadings.Scheduled ->
                        Readings(
                            day = day,
                            layout = layout,
                            scale = scale,
                            showHeader = showsHeader(layout, size),
                        )
                }
        }
    }
}

/**
 * The one balanced readings layout, parameterized per tier: an optional fixed-height date
 * header, then the three reading rows each weighted to an equal share of the remaining
 * height (vertically centered within their share) — the card is always filled, never
 * top-stacked (D-S14-2).
 */
@Composable
private fun ColumnScope.Readings(
    day: DayReadings.Scheduled,
    layout: WidgetLayout,
    scale: WidgetScale,
    showHeader: Boolean,
) {
    val abbreviated = layout == WidgetLayout.SMALL || layout == WidgetLayout.TINY
    if (showHeader) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Header(day.date, layout, scale)
            Spacer(modifier = GlanceModifier.defaultWeight())
            if (day.dayComplete) {
                if (layout == WidgetLayout.LARGE) {
                    Text(
                        text = LocalContext.current.getString(R.string.day_complete),
                        style =
                            TextStyle(
                                color = GlanceTheme.colors.primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        modifier = GlanceModifier.semantics { testTag = "widget-day-complete" },
                    )
                } else {
                    DayCompleteBadge(fontSize = scale.dateSp)
                }
            }
        }
    }
    day.readings.forEach { reading ->
        Row(
            // Each reading takes an equal share of the remaining height; centering the row
            // content within that share distributes the rows evenly down the card.
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReadingRow(
                reading = reading,
                showStreamTitle = layout == WidgetLayout.LARGE,
                abbreviated = abbreviated,
                scale = scale,
            )
        }
    }
    // A headerless complete day still speaks its state: a compact badge row under the list.
    if (!showHeader && day.dayComplete) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = GlanceModifier.defaultWeight())
            DayCompleteBadge(fontSize = scale.referenceSp)
        }
    }
}

@Composable
private fun DayCompleteBadge(fontSize: Int) {
    val dayCompleteText = LocalContext.current.getString(R.string.day_complete)
    Text(
        // The badge collapses to a checkmark below LARGE; a11y still speaks the full state.
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
    scale: WidgetScale,
) {
    val context = LocalContext.current
    val stateDescription =
        context.getString(
            if (reading.isRead) R.string.widget_reading_read else R.string.widget_reading_unread,
        )
    Text(
        // Read-only completion mark (ESpec §7); state is also exposed to a11y via the
        // row's contentDescription, since the glyph alone is not accessible.
        text = if (reading.isRead) "✓" else "○",
        style =
            TextStyle(
                color = if (reading.isRead) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant,
                fontSize = scale.markSp.sp,
                fontWeight = FontWeight.Bold,
            ),
        modifier =
            GlanceModifier.semantics {
                testTag = if (reading.isRead) "widget-mark-read" else "widget-mark-unread"
            },
    )
    Spacer(modifier = GlanceModifier.width(scale.markGap.dp))
    val titlePrefix = reading.streamTitle?.let { "$it, " }.orEmpty()
    Column(
        modifier =
            GlanceModifier.semantics {
                // Always the full canonical reference — abbreviations are visual only. The stream
                // title (plan data) is spoken only when the active plan supplies one (D-ALT-22/23).
                contentDescription =
                    "$titlePrefix${ReadingFormatter.format(reading.portion)}, $stateDescription"
            },
    ) {
        // D-ALT-23: a single-stream plan supplies no title — render the reference alone.
        if (showStreamTitle && reading.streamTitle != null) {
            Text(
                text = reading.streamTitle,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = scale.titleSp.sp),
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
                    fontSize = scale.referenceSp.sp,
                    fontWeight = FontWeight.Medium,
                ),
            maxLines = 1,
        )
    }
}

/** Feb 29 (decision D1): centered both axes at every size — a calm card, not a corner note. */
@Composable
private fun ColumnScope.NoReadings(
    date: LocalDate,
    layout: WidgetLayout,
    showHeader: Boolean,
) {
    val noReadingsText = LocalContext.current.getString(R.string.no_scheduled_readings_feb29)
    val scale = scaleFor(layout)
    if (showHeader) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Header(date, layout, scale)
        }
    }
    Column(
        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (layout == WidgetLayout.SMALL || layout == WidgetLayout.TINY) {
            Text(
                text = "—",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 22.sp),
                modifier =
                    GlanceModifier.semantics {
                        testTag = "widget-no-readings"
                        contentDescription = noReadingsText
                    },
            )
        } else {
            Text(
                text = noReadingsText,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = scale.referenceSp.sp),
                modifier = GlanceModifier.semantics { testTag = "widget-no-readings" },
            )
        }
    }
}

/** Load failure (mirrors D-S4-3): centered, spoken, and still one tap into the app. */
@Composable
private fun ColumnScope.LoadFailed(layout: WidgetLayout) {
    val loadFailedText = LocalContext.current.getString(R.string.widget_load_failed)
    Column(
        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (layout == WidgetLayout.SMALL || layout == WidgetLayout.TINY) {
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
                text = loadFailedText,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = scaleFor(layout).referenceSp.sp),
                modifier = GlanceModifier.semantics { testTag = "widget-error" },
            )
        }
    }
}

@Composable
private fun Header(
    date: LocalDate,
    layout: WidgetLayout,
    scale: WidgetScale,
) {
    Text(
        text = date.format(if (layout == WidgetLayout.SMALL) ShortDateFormat else HeaderDateFormat),
        style =
            TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = scale.dateSp.sp,
                fontWeight = FontWeight.Bold,
            ),
        modifier = GlanceModifier.semantics { testTag = "widget-date" },
    )
}

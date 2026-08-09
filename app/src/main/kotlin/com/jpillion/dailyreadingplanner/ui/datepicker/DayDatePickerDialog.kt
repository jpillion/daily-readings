package com.jpillion.dailyreadingplanner.ui.datepicker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpillion.dailyreadingplanner.R
import com.jpillion.dailyreadingplanner.domain.model.DayCompletion
import com.jpillion.dailyreadingplanner.platform.AndroidDateTextFormatter
import com.jpillion.dailyreadingplanner.platform.DateTextFormatter
import com.jpillion.dailyreadingplanner.ui.theme.IndicatorGreenDark
import com.jpillion.dailyreadingplanner.ui.theme.IndicatorGreenLight
import com.jpillion.dailyreadingplanner.ui.theme.IndicatorRedDark
import com.jpillion.dailyreadingplanner.ui.theme.IndicatorRedLight
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Month/day date picker (FR-5) as a dialog over the day pager (D-S5-2), rebuilt in Sprint 8
 * as a custom calendar grid (D-S8-2): each day carries a completion indicator — a green dot
 * for a fully read day (past, today, or future), a red dot for a *past* day with readings
 * missed, nothing for incomplete today/future days or Feb 29 (D1). The dot is backed by a
 * spoken state in the cell's contentDescription, so the signal is never color alone.
 *
 * **Sprint 21 (this picker only):**
 *  - **One-tap selection (BACKLOG #7):** tapping a day cell selects that full date and closes
 *    the dialog in one tap — [onConfirm] fires immediately. There is no separate confirm step
 *    (selecting a date is non-destructive — it just navigates the day pager). Cancel/dismiss
 *    still backs out without selecting.
 *  - **Cross-year month swipe (BACKLOG #6, supersedes the pinned-year part of D-S5-3 *for the
 *    picker*):** the months sit on a [HorizontalPager], so the user swipes left/right across
 *    month — and year — boundaries (Dec → Jan of the next year and back). The chevrons drive
 *    the same pager. The grid is no longer pinned to a single year; completion dots key to
 *    full dates ([completionFor] is queried per displayed [YearMonth]), so the existing dot
 *    semantics carry over at every reachable month.
 */
@Composable
fun DayDatePickerDialog(
    today: LocalDate,
    initialDate: LocalDate,
    completionFor: (YearMonth) -> StateFlow<Map<LocalDate, DayCompletion>>,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    // p1-01: localized date text comes from the platform seam. Defaulted so every existing
    // caller (and the tests that pin real English output) is unchanged.
    formatter: DateTextFormatter = AndroidDateTextFormatter,
) {
    val initialMonth = YearMonth.from(initialDate)
    val pagerState = rememberPagerState(initialPage = MONTH_CENTER_PAGE) { MONTH_PAGE_COUNT }
    val scope = rememberCoroutineScope()
    val displayedMonth = monthForPage(initialMonth, pagerState.currentPage)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.width(360.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp)) {
                MonthHeader(
                    month = displayedMonth,
                    formatter = formatter,
                    onPreviousMonth = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    },
                    onNextMonth = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                )
                val firstDayOfWeek = formatter.firstDayOfWeek()
                WeekdayHeader(firstDayOfWeek, formatter)
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().testTag("picker-month-pager"),
                    key = { it },
                ) { page ->
                    val month = monthForPage(initialMonth, page)
                    val completion by completionFor(month).collectAsStateWithLifecycle()
                    MonthGrid(
                        month = month,
                        firstDayOfWeek = firstDayOfWeek,
                        today = today,
                        completion = completion,
                        // One-tap: select this full date and close immediately (BACKLOG #7).
                        onSelect = onConfirm,
                        formatter = formatter,
                    )
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Box(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("date-picker-cancel"),
                    ) { Text(text = stringResource(R.string.date_picker_cancel)) }
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    formatter: DateTextFormatter,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatter.monthYear(month.atDay(1)),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 8.dp).weight(1f).testTag("picker-month-title"),
        )
        // No year bound any more (BACKLOG #6): the chevrons swipe freely across years.
        IconButton(
            onClick = onPreviousMonth,
            modifier = Modifier.testTag("picker-prev-month"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.previous_month),
            )
        }
        IconButton(
            onClick = onNextMonth,
            modifier = Modifier.testTag("picker-next-month"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.next_month),
            )
        }
    }
}

@Composable
private fun WeekdayHeader(
    firstDayOfWeek: DayOfWeek,
    formatter: DateTextFormatter,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        weekdayOrder(firstDayOfWeek).forEach { dayOfWeek ->
            Box(
                modifier = Modifier.weight(1f).height(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = formatter.weekdayInitial(dayOfWeek),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    firstDayOfWeek: DayOfWeek,
    today: LocalDate,
    completion: Map<LocalDate, DayCompletion>,
    onSelect: (LocalDate) -> Unit,
    formatter: DateTextFormatter,
) {
    val leading = leadingEmptyCells(month, firstDayOfWeek)
    val totalCells = leading + month.lengthOfMonth()
    val rows = (totalCells + 6) / 7
    Column(modifier = Modifier.fillMaxWidth()) {
        repeat(rows) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val cell = row * 7 + column
                    val day = cell - leading + 1
                    if (day in 1..month.lengthOfMonth()) {
                        DayCell(
                            date = month.atDay(day),
                            isToday = month.atDay(day) == today,
                            completion = completion[month.atDay(day)] ?: DayCompletion.NONE,
                            onSelect = onSelect,
                            formatter = formatter,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Box(modifier = Modifier.weight(1f).height(48.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    completion: DayCompletion,
    onSelect: (LocalDate) -> Unit,
    formatter: DateTextFormatter,
    modifier: Modifier = Modifier,
) {
    val onDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val dotColor =
        when (completion) {
            DayCompletion.COMPLETE -> if (onDarkSurface) IndicatorGreenDark else IndicatorGreenLight
            DayCompletion.MISSED -> if (onDarkSurface) IndicatorRedDark else IndicatorRedLight
            DayCompletion.NONE -> null
        }
    val stateText =
        when (completion) {
            DayCompletion.COMPLETE -> stringResource(R.string.day_complete)
            DayCompletion.MISSED -> stringResource(R.string.day_missed)
            DayCompletion.NONE -> null
        }
    val dateText = formatter.fullDate(date)
    val description = if (stateText != null) "$dateText, $stateText" else dateText
    Box(
        modifier =
            modifier
                .height(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .then(
                    if (isToday) {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    },
                ).selectable(
                    // One-tap select-and-close: no persistent selected state lives here now.
                    selected = false,
                    role = Role.Button,
                    onClick = { onSelect(date) },
                ).testTag("picker-day-${date.dayOfMonth}")
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (isToday) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
            // The completion dot (D-S8-2); redundant with the spoken state above.
            Box(
                modifier =
                    Modifier
                        .padding(top = 2.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(dotColor ?: androidx.compose.ui.graphics.Color.Transparent),
            )
        }
    }
}

/**
 * Month-pager geometry (BACKLOG #6), mirroring the day-pager idiom (D-S5-4): a bounded window
 * of [MONTH_PAGE_COUNT] real months centered on the initial month at [MONTH_CENTER_PAGE].
 * ±[MONTH_WINDOW] months ≈ 250 years each way — swiping crosses year boundaries freely.
 */
internal const val MONTH_WINDOW = 3_000
internal const val MONTH_CENTER_PAGE = MONTH_WINDOW
internal const val MONTH_PAGE_COUNT = 2 * MONTH_WINDOW + 1

/** The [YearMonth] shown on [page], measured as an offset from [initialMonth] at the center page. */
internal fun monthForPage(
    initialMonth: YearMonth,
    page: Int,
): YearMonth = initialMonth.plusMonths((page - MONTH_CENTER_PAGE).toLong())

/** Number of blank cells before day 1 when the week starts on [firstDayOfWeek]. */
internal fun leadingEmptyCells(
    month: YearMonth,
    firstDayOfWeek: DayOfWeek,
): Int = ((month.atDay(1).dayOfWeek.value - firstDayOfWeek.value) + 7) % 7

/** The seven weekdays in display order starting from [firstDayOfWeek]. */
internal fun weekdayOrder(firstDayOfWeek: DayOfWeek): List<DayOfWeek> = (0L until 7L).map(firstDayOfWeek::plus)

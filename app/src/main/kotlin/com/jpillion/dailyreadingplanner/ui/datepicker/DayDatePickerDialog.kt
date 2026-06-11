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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
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
import com.jpillion.dailyreadingplanner.ui.theme.IndicatorGreenDark
import com.jpillion.dailyreadingplanner.ui.theme.IndicatorGreenLight
import com.jpillion.dailyreadingplanner.ui.theme.IndicatorRedDark
import com.jpillion.dailyreadingplanner.ui.theme.IndicatorRedLight
import kotlinx.coroutines.flow.StateFlow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Month/day date picker (FR-5) as a dialog over the day pager (D-S5-2), rebuilt in Sprint 8
 * as a custom calendar grid (D-S8-2): the M3 [androidx.compose.material3.DatePicker] offers no
 * per-day-cell slot, and each day here carries a completion indicator — a green dot for a fully
 * read day (past, today, or future), a red dot for a *past* day with readings missed, nothing
 * for incomplete today/future days or Feb 29 (D1). The dot is backed by a spoken state in the
 * cell's contentDescription, so the signal is never color alone.
 *
 * Year semantics are unchanged (D-S5-3, ESpec §6.1): the grid is pinned to [year] — picking a
 * month/day always targets the current year's occurrence; in a leap year Feb 29 is selectable
 * and shows the no-readings state. Swiping, by contrast, steps real dates across year
 * boundaries.
 */
@Composable
fun DayDatePickerDialog(
    year: Int,
    today: LocalDate,
    initialDate: LocalDate,
    completionFor: (YearMonth) -> StateFlow<Map<LocalDate, DayCompletion>>,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // withYear: a leap-day initial date anchors to Feb 28 in a common year (documented D-S5-3).
    var selectedEpochDay by rememberSaveable {
        mutableLongStateOf(initialDate.withYear(year).toEpochDay())
    }
    val selectedDate = LocalDate.ofEpochDay(selectedEpochDay)
    var displayedMonth by rememberSaveable { mutableIntStateOf(selectedDate.monthValue) }
    val month = YearMonth.of(year, displayedMonth)
    val completion by completionFor(month).collectAsStateWithLifecycle()

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
                    month = month,
                    onPreviousMonth = { displayedMonth -= 1 },
                    onNextMonth = { displayedMonth += 1 },
                )
                // Locale read observably via LocalConfiguration (lint: NonObservableLocale).
                val locale = LocalConfiguration.current.locales[0]
                val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
                WeekdayHeader(firstDayOfWeek, locale)
                MonthGrid(
                    month = month,
                    firstDayOfWeek = firstDayOfWeek,
                    selectedDate = selectedDate,
                    today = today,
                    completion = completion,
                    onSelect = { selectedEpochDay = it.toEpochDay() },
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Box(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("date-picker-cancel"),
                    ) { Text(text = stringResource(R.string.date_picker_cancel)) }
                    TextButton(
                        onClick = { onConfirm(selectedDate) },
                        modifier = Modifier.testTag("date-picker-confirm"),
                    ) { Text(text = stringResource(R.string.date_picker_confirm)) }
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = month.format(MonthTitleFormat),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 8.dp).weight(1f).testTag("picker-month-title"),
        )
        IconButton(
            onClick = onPreviousMonth,
            enabled = month.monthValue > 1,
            modifier = Modifier.testTag("picker-prev-month"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.previous_month),
            )
        }
        IconButton(
            onClick = onNextMonth,
            enabled = month.monthValue < 12,
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
    locale: Locale,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        weekdayOrder(firstDayOfWeek).forEach { dayOfWeek ->
            Box(
                modifier = Modifier.weight(1f).height(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
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
    selectedDate: LocalDate,
    today: LocalDate,
    completion: Map<LocalDate, DayCompletion>,
    onSelect: (LocalDate) -> Unit,
) {
    val leading = leadingEmptyCells(month, firstDayOfWeek)
    val totalCells = leading + month.lengthOfMonth()
    val rows = (totalCells + 6) / 7
    repeat(rows) { row ->
        Row(modifier = Modifier.fillMaxWidth()) {
            repeat(7) { column ->
                val cell = row * 7 + column
                val day = cell - leading + 1
                if (day in 1..month.lengthOfMonth()) {
                    DayCell(
                        date = month.atDay(day),
                        selected = month.atDay(day) == selectedDate,
                        isToday = month.atDay(day) == today,
                        completion = completion[month.atDay(day)] ?: DayCompletion.NONE,
                        onSelect = onSelect,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Box(modifier = Modifier.weight(1f).height(48.dp))
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    completion: DayCompletion,
    onSelect: (LocalDate) -> Unit,
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
    val dateText = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
    val description = if (stateText != null) "$dateText, $stateText" else dateText
    Box(
        modifier =
            modifier
                .height(48.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                ).then(
                    if (isToday && !selected) {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    },
                ).selectable(
                    selected = selected,
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
                    when {
                        selected -> MaterialTheme.colorScheme.onPrimary
                        isToday -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
            )
            // The completion dot (D-S8-2); redundant with the spoken state above.
            Box(
                modifier =
                    Modifier
                        .padding(top = 2.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                dotColor == null -> androidx.compose.ui.graphics.Color.Transparent
                                selected -> MaterialTheme.colorScheme.onPrimary
                                else -> dotColor
                            },
                        ),
            )
        }
    }
}

private val MonthTitleFormat = DateTimeFormatter.ofPattern("MMMM uuuu", Locale.getDefault())

/** Number of blank cells before day 1 when the week starts on [firstDayOfWeek]. */
internal fun leadingEmptyCells(
    month: YearMonth,
    firstDayOfWeek: DayOfWeek,
): Int = ((month.atDay(1).dayOfWeek.value - firstDayOfWeek.value) + 7) % 7

/** The seven weekdays in display order starting from [firstDayOfWeek]. */
internal fun weekdayOrder(firstDayOfWeek: DayOfWeek): List<DayOfWeek> = (0L until 7L).map(firstDayOfWeek::plus)

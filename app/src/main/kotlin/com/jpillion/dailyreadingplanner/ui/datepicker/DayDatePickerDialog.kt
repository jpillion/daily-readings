package com.jpillion.dailyreadingplanner.ui.datepicker

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.jpillion.dailyreadingplanner.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Month/day date picker (FR-5) as an M3 dialog over the day pager (D-S5-2). The year range is
 * pinned to [year] — the current year at ViewModel creation — which implements ESpec §6.1's
 * year semantics (D-S5-3): picking a month/day always targets the *current year's* occurrence.
 * (The year is visible in the M3 headline but not navigable; a fully year-less custom
 * calendar was traded away as V1 over-build.) In a leap year Feb 29 is selectable and shows
 * the no-readings state. Swiping, by contrast, steps real dates across year boundaries.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDatePickerDialog(
    year: Int,
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis = localDateToUtcMillis(initialDate.withYear(year)),
            yearRange = IntRange(year, year),
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onConfirm(utcMillisToLocalDate(it)) } },
                enabled = state.selectedDateMillis != null,
                modifier = Modifier.testTag("date-picker-confirm"),
            ) { Text(text = stringResource(R.string.date_picker_confirm)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("date-picker-cancel"),
            ) { Text(text = stringResource(R.string.date_picker_cancel)) }
        },
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

/** M3 DatePicker speaks UTC-midnight epoch millis; these two are the lossless bridge. */
internal fun utcMillisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).toLocalDate()

internal fun localDateToUtcMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

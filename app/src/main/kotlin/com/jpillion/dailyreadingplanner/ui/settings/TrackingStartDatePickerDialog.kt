package com.jpillion.dailyreadingplanner.ui.settings

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
 * Stock M3 full-calendar-date picker (S10; extracted to its own file in S19 — it now also
 * serves the first-run prompt's "Pick a date" path,
 * [TrackingStartPromptDialog][com.jpillion.dailyreadingplanner.ui.day.TrackingStartPromptDialog]): unlike the schedule's pinned-year
 * [DayDatePickerDialog][com.jpillion.dailyreadingplanner.ui.datepicker.DayDatePickerDialog],
 * the tracking start is a real year-qualified date, so year navigation matters here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrackingStartDatePickerDialog(
    initialDate: LocalDate?,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis =
                (initialDate ?: LocalDate.now())
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli(),
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis ?: return@TextButton
                    onConfirm(
                        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(),
                    )
                },
                enabled = state.selectedDateMillis != null,
                modifier = Modifier.testTag("tracking-start-confirm"),
            ) { Text(text = stringResource(R.string.tracking_start_dialog_confirm)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("tracking-start-cancel"),
            ) { Text(text = stringResource(R.string.tracking_start_dialog_cancel)) }
        },
        modifier = Modifier.testTag("tracking-start-dialog"),
    ) {
        DatePicker(state = state)
    }
}

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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

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
            // p1-02: `LocalDate.now()` here read the SYSTEM DEFAULT zone with no injected clock —
            // a pre-existing quirk of this fallback, deliberately preserved rather than corrected.
            // M3's DatePicker speaks UTC-midnight millis, so the two zones below are different on
            // purpose and were before this change too.
            initialSelectedDateMillis =
                (
                    initialDate ?: Clock.System
                        .now()
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                        .date
                ).atStartOfDayIn(TimeZone.UTC)
                    .toEpochMilliseconds(),
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis ?: return@TextButton
                    onConfirm(
                        Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date,
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

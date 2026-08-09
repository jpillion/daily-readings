package com.jpillion.dailyreadingplanner.ui.day

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jpillion.dailyreadingplanner.R
import com.jpillion.dailyreadingplanner.platform.AndroidDateTextFormatter
import com.jpillion.dailyreadingplanner.platform.DateTextFormatter
import com.jpillion.dailyreadingplanner.ui.settings.TrackingStartDatePickerDialog
import java.time.LocalDate

/**
 * The one-time first-run tracking-start prompt (S19, D-S19-1): exactly three choices —
 * January 1 of the current year, today, or a custom date via the shared full-calendar
 * [TrackingStartDatePickerDialog]. One sentence of plain copy explains what the start date
 * means, plus the reassurance that it's changeable in Settings.
 *
 * Dismissing (back/scrim) calls [onDismiss] — the caller applies the Jan-1 fallback and never
 * re-shows. Canceling the custom *picker* is NOT an answer: it returns to this prompt.
 */
@Composable
internal fun TrackingStartPromptDialog(
    today: LocalDate,
    onChoose: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    // p1-01: localized date text comes from the platform seam; defaulted so callers/tests are
    // unchanged.
    formatter: DateTextFormatter = AndroidDateTextFormatter,
) {
    var showCustomPicker by rememberSaveable { mutableStateOf(false) }
    if (showCustomPicker) {
        TrackingStartDatePickerDialog(
            initialDate = today,
            onConfirm = onChoose,
            onDismiss = { showCustomPicker = false },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.tracking_prompt_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.tracking_prompt_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                PromptOptionRow(
                    text = stringResource(R.string.tracking_prompt_jan1, today.year),
                    tag = "tracking-prompt-jan1",
                    onClick = { onChoose(LocalDate.of(today.year, 1, 1)) },
                )
                PromptOptionRow(
                    text =
                        stringResource(
                            R.string.tracking_prompt_today,
                            formatter.mediumDate(today),
                        ),
                    tag = "tracking-prompt-today",
                    onClick = { onChoose(today) },
                )
                PromptOptionRow(
                    text = stringResource(R.string.tracking_prompt_custom),
                    tag = "tracking-prompt-custom",
                    onClick = { showCustomPicker = true },
                )
            }
        },
        confirmButton = {},
        modifier = Modifier.testTag("tracking-start-prompt"),
    )
}

/** An authored 48dp option row (a11y gate): full text spoken, Button role. */
@Composable
internal fun PromptOptionRow(
    text: String,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .selectable(
                    selected = false,
                    role = Role.Button,
                    onClick = onClick,
                ).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

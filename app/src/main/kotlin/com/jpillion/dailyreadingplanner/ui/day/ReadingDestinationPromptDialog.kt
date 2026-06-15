package com.jpillion.dailyreadingplanner.ui.day

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.jpillion.dailyreadingplanner.R
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestinationMode

/**
 * VD-T7 (D-V3-19) — the first-run reading-destination question for a fresh install: read in the
 * in-app Bible, or open an external app/site. In-app is NEVER pre-selected as a silent default —
 * both choices are equal, explicit taps (OQ-1: neutral presentation). Dismissing (back/scrim) is
 * NOT an answer; the caller leaves the prompt resolved-but-unanswered so it re-asks next launch.
 */
@Composable
internal fun ReadingDestinationPromptDialog(
    onChoose: (ReadingDestinationMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.reading_destination_prompt_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.reading_destination_prompt_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                PromptOptionRow(
                    text = stringResource(R.string.reading_destination_prompt_inapp),
                    tag = "reading-destination-prompt-inapp",
                    onClick = { onChoose(ReadingDestinationMode.IN_APP) },
                )
                PromptOptionRow(
                    text = stringResource(R.string.reading_destination_prompt_external),
                    tag = "reading-destination-prompt-external",
                    onClick = { onChoose(ReadingDestinationMode.EXTERNAL) },
                )
            }
        },
        confirmButton = {},
        modifier = Modifier.testTag("reading-destination-prompt"),
    )
}

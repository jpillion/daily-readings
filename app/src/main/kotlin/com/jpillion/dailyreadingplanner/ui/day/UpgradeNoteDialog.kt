package com.jpillion.dailyreadingplanner.ui.day

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.jpillion.dailyreadingplanner.R

/**
 * VD-T10 (OQ-2) — the one-time "the in-app Bible is here" note for EXISTING users. Informational:
 * the user's external reading destination is preserved on dismiss; "Use it now" opts them into the
 * in-app reader. Either action marks the note shown so it never re-appears.
 */
@Composable
internal fun UpgradeNoteDialog(onDismiss: (switchToInApp: Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { onDismiss(false) },
        title = { Text(text = stringResource(R.string.upgrade_note_title)) },
        text = { Text(text = stringResource(R.string.upgrade_note_body)) },
        confirmButton = {
            TextButton(
                onClick = { onDismiss(true) },
                modifier = Modifier.testTag("upgrade-note-use-it-now"),
            ) { Text(text = stringResource(R.string.upgrade_note_use_it_now)) }
        },
        dismissButton = {
            TextButton(
                onClick = { onDismiss(false) },
                modifier = Modifier.testTag("upgrade-note-dismiss"),
            ) { Text(text = stringResource(R.string.upgrade_note_keep_current)) }
        },
        modifier = Modifier.testTag("upgrade-note"),
    )
}

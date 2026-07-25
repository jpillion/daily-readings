package com.jpillion.dailyreadingplanner.bible.ui.reader

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jpillion.dailyreadingplanner.R

/**
 * Q2 / Ticket 1 — the contextual action bar. While a [VerseSelection] is active this REPLACES the
 * reader's normal top bar (the standard Android multi-select idiom): the selected count, a Copy
 * action, and an X that exits. The reader's own top bar (pencil + chapter heading + version) is
 * untouched and returns the moment the selection ends.
 *
 * A11y, per the spec's first-class requirements:
 *  - the count is a **polite live region**, so entering selection mode and every subsequent count
 *    change is announced without the user having to hunt for the bar;
 *  - Copy is a **[TextButton] with a visible word**, not an icon. `Icons.Filled.ContentCopy` does
 *    not exist in the frozen `material-icons-core` 1.7.8, and a spoken word beats a guessed glyph
 *    regardless — this follows the `UpdateRestartSnackbarEffect` "Restart" precedent;
 *  - both controls carry ≥48dp touch bounds (pinned by `AccessibilityGateTest`).
 *
 * Exits are X, system back, and deselecting the last verse. **Copy is deliberately NOT an exit**
 * (P-Q-1) — see [ReaderScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerseSelectionBar(
    count: Int,
    onCopy: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onClose, modifier = Modifier.testTag("verse-selection-close")) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.verse_selection_close),
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.verse_selection_count, count),
                modifier =
                    Modifier
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag("verse-selection-count"),
            )
        },
        actions = {
            TextButton(
                onClick = onCopy,
                modifier = Modifier.heightIn(min = 48.dp).testTag("verse-selection-copy"),
            ) {
                Text(stringResource(R.string.verse_selection_copy))
            }
        },
    )
}

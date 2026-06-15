package com.jpillion.dailyreadingplanner.bible.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jpillion.dailyreadingplanner.R
import com.jpillion.dailyreadingplanner.bible.domain.model.BibleTranslation

/**
 * Owner request (Priya's design) — the reader top-bar version control, placed on the RIGHT of the
 * single-line top bar (the pencil + chapter heading are on the left).
 *
 * D-N-3 — version-count drives the SHAPE:
 *  - **0 or 1 version** (today: only KJV is bundled): a STATIC TITLE — the version [BibleTranslation.code]
 *    ("KJV") as plain [MaterialTheme.typography.labelLarge]/onSurfaceVariant text, NOT a control. It is
 *    spoken to TalkBack as the unabbreviated [BibleTranslation.name] ("King James Version", D-N-2) so the
 *    screen-reader announces the full translation, not an abbreviation.
 *  - **more than one version** (future, V4 multi-translation): an M3 [DropdownMenu] to switch — the
 *    Settings dropdown idiom, announced as a selector. This branch is structurally present and tested,
 *    but unexercised in production until a second version exists.
 *
 * The label is sourced from the asset data (the seam's `translations()`), never a hardcoded literal.
 * Renders nothing while the version list is still empty (the brief pre-load state).
 */
@Composable
fun ReaderVersionSelector(
    versions: List<BibleTranslation>,
    selected: BibleTranslation?,
    onSelect: (BibleTranslation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = selected ?: versions.firstOrNull() ?: return
    if (versions.size <= 1) {
        // D-N-3 single-version branch: a static title, NOT a control.
        Text(
            text = current.code,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                modifier
                    .padding(end = 16.dp)
                    .semantics { contentDescription = current.name }
                    .testTag("reader-version-title"),
        )
        return
    }
    // D-N-3 multi-version branch: an M3 dropdown to switch versions.
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rowDescription = stringResource(R.string.reader_version_dropdown_description, current.name)
    Box {
        Row(
            modifier =
                modifier
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = false,
                        role = Role.DropdownList,
                        onClick = { expanded = true },
                    ).testTag("reader-version-dropdown")
                    .semantics { contentDescription = rowDescription }
                    .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = current.code, style = MaterialTheme.typography.labelLarge)
            Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            versions.forEach { version ->
                DropdownMenuItem(
                    text = { Text(text = version.name) },
                    onClick = {
                        expanded = false
                        onSelect(version)
                    },
                    leadingIcon =
                        if (version == current) {
                            { Icon(imageVector = Icons.Filled.Check, contentDescription = null) }
                        } else {
                            null
                        },
                    modifier = Modifier.testTag("reader-version-option-${version.code}"),
                )
            }
        }
    }
}

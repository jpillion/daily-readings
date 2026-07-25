package com.jpillion.dailyreadingplanner.bible.ui.reader

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jpillion.dailyreadingplanner.R
import com.jpillion.dailyreadingplanner.domain.model.ExternalBibleApp
import com.jpillion.dailyreadingplanner.ui.day.externalBibleAppNameRes

/**
 * Q2 / Ticket 2 — the per-verse action menu. A short tap on a verse (when NOT selecting) opens this
 * M3 [DropdownMenu], anchored at the tapped verse; every item dismisses the menu before acting.
 *
 * The three items are owner-chosen and owner-ordered. **Share was explicitly declined — do not add
 * it.** "Open in `<app>`" is the Sprint-H verse tap-out, now one deliberate step behind a tap
 * instead of firing on every touch (the owner's complaint: "the click takes you out"); it still
 * resolves through the UNCHANGED `OpenVerseUseCase`, whose current contract is **D-23-1**
 * (Sprint K): a verse tap is always external by definition, so the destination resolves from the
 * chosen [ExternalBibleApp] ALONE — independent of the
 * [com.jpillion.dailyreadingplanner.domain.model.ReadingDestinationMode], with MySword taking the
 * app-intent + BLB-verse-fallback path and the persisted choice never rewritten. (This supersedes
 * the H4 "map IN_APP -> BLB" shim: an in-app-mode user keeps their remembered external app, which
 * is why the item's label is that app's name.)
 * "Select verses" is the same entry point as a long-press, and is the TalkBack-reachable one.
 *
 * **The menu exists to be GROWN.** The owner's stated reason for it is the slot it creates for
 * future per-verse features — **cross-references is the named next one**. The items are therefore a
 * flat, ordered list of [VerseActionItem] calls: adding cross-references is one more line here plus
 * one string and one callback, never a restructure.
 */
@Composable
fun VerseActionMenu(
    expanded: Boolean,
    externalApp: ExternalBibleApp,
    onDismissRequest: () -> Unit,
    onOpenExternally: () -> Unit,
    onCopy: () -> Unit,
    onSelectVerses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appName = stringResource(externalBibleAppNameRes(externalApp))
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest, modifier = modifier) {
        VerseActionItem(
            text = stringResource(R.string.verse_menu_open_in, appName),
            tag = "verse-menu-open",
            onClick = onOpenExternally,
        )
        VerseActionItem(
            text = stringResource(R.string.verse_menu_copy),
            tag = "verse-menu-copy",
            onClick = onCopy,
        )
        VerseActionItem(
            text = stringResource(R.string.verse_menu_select),
            tag = "verse-menu-select",
            onClick = onSelectVerses,
        )
    }
}

/**
 * One menu entry: visible text, a stable test tag, and a ≥48dp target (the M3 default menu-item
 * height is already 48dp; the explicit floor keeps the a11y gate honest if the default ever moves).
 * The caller dismisses the menu inside [onClick] — an action never leaves the menu open.
 */
@Composable
private fun VerseActionItem(
    text: String,
    tag: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp).testTag(tag),
    )
}

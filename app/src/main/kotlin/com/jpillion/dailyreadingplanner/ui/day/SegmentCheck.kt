package com.jpillion.dailyreadingplanner.ui.day

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.jpillion.dailyreadingplanner.R
import com.jpillion.dailyreadingplanner.domain.model.ReadingCheckState

/**
 * The tri-state check on one reading card (= one D-SEG-1 segment), sprint-00P.
 *
 * Stateless and transition-free: it takes [onClick], not `onCheckedChange`, because the next state
 * is decided by the pure `SegmentCheckPolicy` upstream, never by a boolean flip here. Every state is
 * clickable, including [ReadingCheckState.COMPLETE] — a completed reading must stay un-checkable.
 *
 * Rendering:
 * - [ReadingCheckState.UNCHECKED] / [ReadingCheckState.COMPLETE] — the stock M3 [Checkbox] with
 *   **default** [CheckboxDefaults.colors], byte-for-byte today's appearance (spec: keep COMPLETE
 *   exactly as it is).
 * - [ReadingCheckState.PARTIAL] — still a *checked* checkbox (a tick, deliberately NOT an M3
 *   indeterminate dash: the segment IS read, the reading just isn't finished), tinted with
 *   [SegmentCheckColors.partial] / [SegmentCheckColors.partialCheckmark].
 *
 * A11y: never color alone — the control carries a spoken `stateDescription` ("not read" /
 * "partially read" / "read") that replaces TalkBack's generic "ticked/not ticked", so PARTIAL and
 * COMPLETE are distinguishable without seeing the hue. The ≥48dp touch target of today's card
 * checkbox is preserved. The caller supplies the `testTag` through [modifier] (the tag is
 * segment-indexed at the call site), so none is hardcoded here.
 */
@Composable
fun SegmentCheckbox(
    state: ReadingCheckState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: SegmentCheckColors = defaultSegmentCheckColors(),
) {
    val spokenState =
        stringResource(
            when (state) {
                ReadingCheckState.UNCHECKED -> R.string.segment_state_not_read
                ReadingCheckState.PARTIAL -> R.string.segment_state_partially_read
                ReadingCheckState.COMPLETE -> R.string.segment_state_read
            },
        )
    // PARTIAL is a checked box in the partial hue: `checkedColor` is the box fill, `checkmarkColor`
    // the tick inside it (the two params the M3 CheckboxDefaults.colors factory actually exposes —
    // there is no checkedBoxColor/checkedCheckmarkColor on the factory; those names live on
    // CheckboxColors itself). Every other state takes the untouched defaults.
    val checkboxColors =
        when (state) {
            ReadingCheckState.PARTIAL ->
                CheckboxDefaults.colors(
                    checkedColor = colors.partial,
                    checkmarkColor = colors.partialCheckmark,
                )
            else -> CheckboxDefaults.colors()
        }
    Checkbox(
        checked = state != ReadingCheckState.UNCHECKED,
        onCheckedChange = { onClick() },
        colors = checkboxColors,
        modifier =
            modifier
                // G-A11Y: keep the check a ≥48dp target (today's ReadingCard treatment).
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .semantics { stateDescription = spokenState },
    )
}

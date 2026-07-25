package com.jpillion.dailyreadingplanner.ui.day

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.jpillion.dailyreadingplanner.ui.theme.IndicatorAmberDark
import com.jpillion.dailyreadingplanner.ui.theme.IndicatorAmberLight
import com.jpillion.dailyreadingplanner.ui.theme.IndicatorOnAmberDark
import com.jpillion.dailyreadingplanner.ui.theme.IndicatorOnAmberLight

/**
 * The tri-state reading check's palette (sprint-00P). THE ONE seam for a future colorblind-friendly
 * palette (owner-deferred, already queued for the year strips): swap the provider —
 * [defaultSegmentCheckColors] — and nothing else moves. Mirrors the S17 `StripColors` precedent
 * deliberately, so the two color-bearing surfaces of the app are re-keyed the same way.
 *
 * Only the PARTIAL state is described here. `UNCHECKED` and `COMPLETE` are rendered by the stock M3
 * `Checkbox` with `CheckboxDefaults.colors()` and are byte-for-byte today's appearance — the spec is
 * explicit that the completed check must not change, so it is NOT parameterized here. Adding a
 * `complete` field would invite exactly the drift the spec forbids.
 *
 * @property partial the checked-box fill for `ReadingCheckState.PARTIAL`.
 * @property partialCheckmark the tick drawn inside that fill — its on-color, so the tick stays
 *   legible in both themes (the light-theme fill is dark, the dark-theme fill is light).
 */
data class SegmentCheckColors(
    val partial: Color,
    val partialCheckmark: Color,
)

/**
 * Theme-derived default palette: the fixed semantic amber pair (progress — neither the completed
 * green nor the missed red), branched on surface luminance exactly like `defaultStripColors()`.
 *
 * The branch is on the *surface* rather than `isSystemInDarkTheme()` on purpose: the app's theme is
 * user-controlled (S6 `ThemeMode`), so the palette must follow the theme actually in composition.
 */
@Composable
fun defaultSegmentCheckColors(): SegmentCheckColors {
    val onDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return SegmentCheckColors(
        partial = if (onDarkSurface) IndicatorAmberDark else IndicatorAmberLight,
        partialCheckmark = if (onDarkSurface) IndicatorOnAmberDark else IndicatorOnAmberLight,
    )
}

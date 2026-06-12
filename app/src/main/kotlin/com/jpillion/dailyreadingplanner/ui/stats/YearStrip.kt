package com.jpillion.dailyreadingplanner.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jpillion.dailyreadingplanner.domain.model.StripDayState
import com.jpillion.dailyreadingplanner.ui.theme.IndicatorGreenDark
import com.jpillion.dailyreadingplanner.ui.theme.IndicatorGreenLight
import com.jpillion.dailyreadingplanner.ui.theme.IndicatorRedDark
import com.jpillion.dailyreadingplanner.ui.theme.IndicatorRedLight

/**
 * The strip palette (S17). The ONE seam for a future colorblind-friendly option (owner-
 * deferred): swap the provider, nothing else moves. Defaults reuse the date-picker dot
 * tokens (D-S17-4) so the strips and the picker indicators can never disagree visually.
 */
data class StripColors(
    val read: Color,
    val missed: Color,
    val neutral: Color,
    val todayMarker: Color,
)

/** Theme-derived default palette: picker-dot green/red, quiet neutral, onSurface tick. */
@Composable
fun defaultStripColors(): StripColors {
    val onDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return StripColors(
        read = if (onDarkSurface) IndicatorGreenDark else IndicatorGreenLight,
        missed = if (onDarkSurface) IndicatorRedDark else IndicatorRedLight,
        neutral = MaterialTheme.colorScheme.surfaceVariant,
        todayMarker = MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * One year strip (S17): a single Canvas painting one contiguous segment per calendar day —
 * ~1dp per segment on a phone; deliberately texture, not tappable days (no touch handling,
 * no per-day composables). The thin [StripColors.todayMarker] tick at today's position is
 * the eye's anchor (owner-approved). Color-only by nature: callers MUST attach a spoken
 * summary (see StatsContent) — this composable carries no semantics of its own.
 */
@Composable
fun YearStrip(
    states: List<StripDayState>,
    todayIndex: Int?,
    colors: StripColors,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(2.dp)),
    ) {
        if (states.isEmpty()) return@Canvas
        val segmentWidth = size.width / states.size
        // S18 (owner): adjacent same-state days must read as ONE continuous band — per-day
        // rects left antialiased hairline seams between equal-color neighbors. Consecutive
        // same-state runs are coalesced into single rects (no interior seams, ~10x fewer
        // draw calls); the first run starts at exactly 0 and the last ends at exactly
        // size.width, so the strip is edge-to-edge regardless of float rounding.
        val runs = coalesceRuns(states)
        runs.forEachIndexed { runIndex, run ->
            val color =
                when (run.state) {
                    StripDayState.READ -> colors.read
                    StripDayState.MISSED -> colors.missed
                    StripDayState.NEUTRAL -> colors.neutral
                }
            val left = run.startIndex * segmentWidth
            val right =
                if (runIndex == runs.lastIndex) {
                    size.width
                } else {
                    (run.startIndex + run.count) * segmentWidth
                }
            drawRect(color = color, topLeft = Offset(left, 0f), size = Size(right - left, size.height))
        }
        if (todayIndex != null && todayIndex in states.indices) {
            val tickWidth = 1.dp.toPx()
            val center = (todayIndex + 0.5f) * segmentWidth
            drawRect(
                color = colors.todayMarker,
                topLeft = Offset((center - tickWidth / 2f).coerceIn(0f, size.width - tickWidth), 0f),
                size = Size(tickWidth, size.height),
            )
        }
    }
}

/** One maximal run of consecutive equal day states (S18): days [startIndex, startIndex + count). */
internal data class StripRun(
    val startIndex: Int,
    val count: Int,
    val state: StripDayState,
)

/**
 * Coalesces [states] into maximal runs of consecutive equal states, in order. The runs
 * partition the input exactly: the first starts at 0, each run starts where the previous
 * ended, and the counts sum to states.size (pinned in YearStripRunsTest).
 */
internal fun coalesceRuns(states: List<StripDayState>): List<StripRun> {
    if (states.isEmpty()) return emptyList()
    val runs = mutableListOf<StripRun>()
    var runStart = 0
    for (index in 1..states.size) {
        if (index == states.size || states[index] != states[runStart]) {
            runs.add(StripRun(startIndex = runStart, count = index - runStart, state = states[runStart]))
            runStart = index
        }
    }
    return runs
}

package com.jpillion.dailyreadingplanner.ui.stats

import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.domain.model.StripDayState
import org.junit.Test

/**
 * S18: the run-coalescing behind the continuous year strips. The contract that makes the
 * strip gap-free and edge-to-edge: the runs PARTITION the day list exactly — first run
 * starts at index 0, every run starts where the previous one ended (shared edges, so no
 * hairline gaps and no overlap), counts sum to the full size, and no two adjacent runs
 * share a state (maximality — adjacent same-color days are always ONE rect).
 */
class YearStripRunsTest {
    @Test
    fun `empty input yields no runs`() {
        assertThat(coalesceRuns(emptyList())).isEmpty()
    }

    @Test
    fun `a uniform year is exactly one run covering every day`() {
        val runs = coalesceRuns(List(365) { StripDayState.NEUTRAL })
        assertThat(runs).containsExactly(StripRun(startIndex = 0, count = 365, state = StripDayState.NEUTRAL))
    }

    @Test
    fun `consecutive same-state days coalesce into single runs in order`() {
        // 120 READ, 3 MISSED, 242 NEUTRAL — the stats fixture shape.
        val states =
            List(120) { StripDayState.READ } + List(3) { StripDayState.MISSED } + List(242) { StripDayState.NEUTRAL }
        assertThat(coalesceRuns(states))
            .containsExactly(
                StripRun(startIndex = 0, count = 120, state = StripDayState.READ),
                StripRun(startIndex = 120, count = 3, state = StripDayState.MISSED),
                StripRun(startIndex = 123, count = 242, state = StripDayState.NEUTRAL),
            ).inOrder()
    }

    @Test
    fun `alternating states never coalesce - one run per day`() {
        val states = List(10) { if (it % 2 == 0) StripDayState.READ else StripDayState.MISSED }
        val runs = coalesceRuns(states)
        assertThat(runs).hasSize(10)
        runs.forEachIndexed { index, run ->
            assertThat(run.startIndex).isEqualTo(index)
            assertThat(run.count).isEqualTo(1)
        }
    }

    @Test
    fun `runs partition the input exactly - contiguous, complete, and maximal`() {
        // A messy mixed year: the structural invariants must hold for any input.
        val states =
            List(366) { index ->
                when {
                    index % 17 == 0 -> StripDayState.MISSED
                    index % 3 == 0 -> StripDayState.READ
                    else -> StripDayState.NEUTRAL
                }
            }
        val runs = coalesceRuns(states)
        // Edge-to-edge: first run starts at 0; counts sum to the whole year.
        assertThat(runs.first().startIndex).isEqualTo(0)
        assertThat(runs.sumOf { it.count }).isEqualTo(366)
        runs.zipWithNext { previous, next ->
            // Shared edges: each run starts exactly where the previous ended.
            assertThat(next.startIndex).isEqualTo(previous.startIndex + previous.count)
            // Maximality: adjacent runs never share a state.
            assertThat(next.state).isNotEqualTo(previous.state)
        }
        // The runs reproduce the input exactly.
        val rebuilt = runs.flatMap { run -> List(run.count) { run.state } }
        assertThat(rebuilt).isEqualTo(states)
    }
}

package com.jpillion.dailyreadingplanner.testing

import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.ReadingCheckState
import com.jpillion.dailyreadingplanner.ui.day.ReadingSegmentUiState

/**
 * Card-state fixture for the day screen (sprint-00P): one card per portion — the single-segment
 * shape that every Bible-Companion reading actually has (`segmentIndex = 0`, `segmentCount = 1`, so
 * `PARTIAL` is unreachable per D-SEG-4 and behaviour is byte-for-byte pre-sprint).
 *
 * Built LITERALLY — deliberately not through the production `segmentUiStates` mapping — so a UI
 * test's expectation can never be rewritten by a mutation in that mapping. The mapping itself is
 * pinned separately, through the real ViewModel, in `DayReadingsViewModelTest`.
 *
 * @param read the stream numbers whose real mark is set (⇒ `COMPLETE`).
 * @param titleFor the plan's stream title, or null for a single-stream plan (D-ALT-23).
 */
fun singleSegmentStates(
    portions: List<Portion>,
    read: Set<Int> = emptySet(),
    titleFor: (Int) -> String? = { null },
): List<ReadingSegmentUiState> =
    portions.map { portion ->
        ReadingSegmentUiState(
            streamNumber = portion.streamNumber,
            segmentIndex = 0,
            segmentCount = 1,
            streamTitle = titleFor(portion.streamNumber),
            portion = portion,
            checkState =
                if (portion.streamNumber in read) {
                    ReadingCheckState.COMPLETE
                } else {
                    ReadingCheckState.UNCHECKED
                },
        )
    }

package com.jpillion.dailyreadingplanner.ui.day

import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.ReadingCheckState
import java.time.LocalDate

/**
 * One reading CARD — a single D-SEG-1 segment of a day's reading (sprint-00P).
 *
 * A day's reading (one [com.jpillion.dailyreadingplanner.domain.model.ReadingStatus] per stream) is
 * split into contiguous passages by `ReadingSegments.segmentsOf`, and each of those becomes one of
 * these. The split happens in [DayReadingsViewModel] and NOWHERE else (D-SEG-8): `DayReadings` /
 * `ReadingStatus` stay per-portion, so the widget, reminders and persistent notification keep their
 * current one-row-per-reading inputs.
 *
 * @property streamNumber the stream this segment belongs to (1..N) — the persisted progress key.
 * @property segmentIndex 0-based index of this segment within its stream's portion (the card/token
 *   index).
 * @property segmentCount how many segments the whole reading has. 1 ⇒ `PARTIAL` is unreachable
 *   (D-SEG-4) and the behaviour is byte-for-byte today's.
 * @property streamTitle the active plan's stream title, or null for a single-stream plan
 *   (D-ALT-23). Repeats on every card of a multi-segment stream (per spec).
 * @property portion the SEGMENT as a portion — same [streamNumber], only this run's refs (D-SEG-6).
 *   This is what a tap hands to `OpenReferenceUseCase`, so the reader/external URL carries exactly
 *   the passage the card names.
 */
data class ReadingSegmentUiState(
    val streamNumber: Int,
    val segmentIndex: Int,
    val segmentCount: Int,
    val streamTitle: String?,
    val portion: Portion,
    val checkState: ReadingCheckState,
)

/**
 * Everything a single day's page can show. Mirrors the domain's sealed day states
 * (DayReadings) plus the two purely-presentational states: initial load and asset failure
 * (D-S4-3 — a load failure degrades to a retryable error screen instead of crashing).
 *
 * Sprint 5 generalized this from TodayUiState: the same four states now describe *any*
 * calendar date, with today as the default (D-S5-1).
 */
sealed interface DayUiState {
    data object Loading : DayUiState

    data class Scheduled(
        val date: LocalDate,
        /**
         * One entry per reading CARD (sprint-00P) — the day's readings split into contiguous
         * passages, flattened in stream order: all of stream 1's segments, then stream 2's, etc.
         * A single-segment reading (the overwhelming majority) contributes exactly one entry, so
         * this is the Bible Companion's familiar three cards.
         */
        val segments: List<ReadingSegmentUiState>,
        val dayComplete: Boolean,
    ) : DayUiState

    /** Feb 29 (decision D1): no readings, no mark controls, no progress. */
    data class NoScheduledReadings(
        val date: LocalDate,
    ) : DayUiState

    /** The plan asset failed to load or validate; offer a retry. */
    data class LoadFailed(
        val date: LocalDate,
    ) : DayUiState
}

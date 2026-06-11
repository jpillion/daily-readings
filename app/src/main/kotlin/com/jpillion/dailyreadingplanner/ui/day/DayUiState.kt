package com.jpillion.dailyreadingplanner.ui.day

import com.jpillion.dailyreadingplanner.domain.model.ReadingStatus
import java.time.LocalDate

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
        val readings: List<ReadingStatus>,
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

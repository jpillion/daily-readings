package com.jpillion.dailyreadingplanner.ui.today

import com.jpillion.dailyreadingplanner.domain.model.ReadingStatus
import java.time.LocalDate

/**
 * Everything the Today screen can show. Mirrors the domain's sealed day states
 * (DayReadings) plus the two purely-presentational states: initial load and asset failure
 * (D-S4-3 — a load failure degrades to a retryable error screen instead of crashing).
 */
sealed interface TodayUiState {
    data object Loading : TodayUiState

    data class Scheduled(
        val date: LocalDate,
        val readings: List<ReadingStatus>,
        val dayComplete: Boolean,
    ) : TodayUiState

    /** Feb 29 (decision D1): no readings, no mark controls, no progress. */
    data class NoScheduledReadings(
        val date: LocalDate,
    ) : TodayUiState

    /** The plan asset failed to load or validate; offer a retry. */
    data class LoadFailed(
        val date: LocalDate,
    ) : TodayUiState
}

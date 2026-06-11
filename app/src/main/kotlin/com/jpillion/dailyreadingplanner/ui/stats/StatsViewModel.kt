package com.jpillion.dailyreadingplanner.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jpillion.dailyreadingplanner.domain.GetReadingStatsUseCase
import com.jpillion.dailyreadingplanner.domain.model.ReadingStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Stats screen state (S11, FR-15/FR-17): nothing but the live [ReadingStats] derivation —
 * the screen is read-only, so there are no actions to expose. `null` until the first
 * emission (the screen renders nothing rather than flashing zeros).
 */
@HiltViewModel
class StatsViewModel
    @Inject
    constructor(
        getReadingStats: GetReadingStatsUseCase,
    ) : ViewModel() {
        val stats: StateFlow<ReadingStats?> =
            getReadingStats().stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )
    }

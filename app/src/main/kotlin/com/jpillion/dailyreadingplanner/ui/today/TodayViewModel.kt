package com.jpillion.dailyreadingplanner.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jpillion.dailyreadingplanner.domain.GetDayReadingsUseCase
import com.jpillion.dailyreadingplanner.domain.MarkWholeDayUseCase
import com.jpillion.dailyreadingplanner.domain.OpenReferenceUseCase
import com.jpillion.dailyreadingplanner.domain.ToggleReadingUseCase
import com.jpillion.dailyreadingplanner.domain.model.DayReadings
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.ReadingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Drives the Today screen over the Sprint 3 use cases. The date is pinned to "today" at
 * creation (the injected Clock keeps it testable); Sprint 5's date picker generalizes this.
 *
 * The BLB URL is delivered as a one-shot event (D-S4-2): the ViewModel computes the URL via
 * [OpenReferenceUseCase]; the UI layer owns the Custom-Tab launch side-effect (ESpec §8).
 */
@HiltViewModel
class TodayViewModel
    @Inject
    constructor(
        getDayReadings: GetDayReadingsUseCase,
        private val toggleReading: ToggleReadingUseCase,
        private val markWholeDay: MarkWholeDayUseCase,
        private val openReference: OpenReferenceUseCase,
        clock: Clock,
    ) : ViewModel() {
        val today: LocalDate = LocalDate.now(clock)

        private val loadAttempt = MutableStateFlow(0)

        @OptIn(ExperimentalCoroutinesApi::class)
        val uiState: StateFlow<TodayUiState> =
            loadAttempt
                .flatMapLatest {
                    getDayReadings(today)
                        .map<DayReadings, TodayUiState> { it.toUiState() }
                        // The loader throws on a missing/invalid asset (a build defect, gate-
                        // verified) — but a release build must degrade, not crash (D-S4-3).
                        .catch { emit(TodayUiState.LoadFailed(today)) }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState.Loading)

        private val openUrlChannel = Channel<String>(Channel.BUFFERED)

        /** One-shot BLB URLs to open in a Custom Tab; collect exactly once from the UI. */
        val openUrlEvents: Flow<String> = openUrlChannel.receiveAsFlow()

        fun onToggleReading(reading: ReadingStatus) {
            viewModelScope.launch {
                toggleReading(today, reading.portion.stream, markRead = !reading.isRead)
            }
        }

        /** One tap: marks all three when the day is incomplete, unmarks all when complete (D-S4-4). */
        fun onMarkWholeDay() {
            val state = uiState.value as? TodayUiState.Scheduled ?: return
            viewModelScope.launch { markWholeDay(today, markRead = !state.dayComplete) }
        }

        fun onReadingTapped(portion: Portion) {
            viewModelScope.launch { openUrlChannel.send(openReference(portion)) }
        }

        fun onRetry() {
            loadAttempt.value += 1
        }

        private fun DayReadings.toUiState(): TodayUiState =
            when (this) {
                is DayReadings.Scheduled -> TodayUiState.Scheduled(date, readings, dayComplete)
                is DayReadings.NoScheduledReadings -> TodayUiState.NoScheduledReadings(date)
            }
    }

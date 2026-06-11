package com.jpillion.dailyreadingplanner.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jpillion.dailyreadingplanner.domain.GetDayReadingsUseCase
import com.jpillion.dailyreadingplanner.domain.GetMonthCompletionUseCase
import com.jpillion.dailyreadingplanner.domain.MarkWholeDayUseCase
import com.jpillion.dailyreadingplanner.domain.OpenReferenceUseCase
import com.jpillion.dailyreadingplanner.domain.ToggleReadingUseCase
import com.jpillion.dailyreadingplanner.domain.model.DayCompletion
import com.jpillion.dailyreadingplanner.domain.model.DayReadings
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.ReadingStatus
import com.jpillion.dailyreadingplanner.widget.WidgetRefresher
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
import java.time.YearMonth
import javax.inject.Inject

/**
 * Drives the day-readings pager over the Sprint 3 use cases, generalizing Sprint 4's
 * TodayViewModel from a single pinned date to *any* calendar date (D-S5-1): each pager page
 * collects [uiStateFor] its own date, and every mark action is parameterized by the date it
 * applies to. [today] stays pinned at creation from the injected Clock — it anchors the pager
 * and the "jump to today" affordance.
 *
 * Year semantics (D-S5-3, ESpec §6.1): callers always pass a *full* LocalDate; progress is
 * keyed by that actual date, so swiping across Dec 31 writes into the adjacent year and marks
 * never collide across years.
 *
 * The BLB URL is delivered as a one-shot event (D-S4-2): the ViewModel computes the URL via
 * [OpenReferenceUseCase]; the UI layer owns the Custom-Tab launch side-effect (ESpec §8).
 */
@HiltViewModel
class DayReadingsViewModel
    @Inject
    constructor(
        private val getDayReadings: GetDayReadingsUseCase,
        private val getMonthCompletion: GetMonthCompletionUseCase,
        private val toggleReading: ToggleReadingUseCase,
        private val markWholeDay: MarkWholeDayUseCase,
        private val openReference: OpenReferenceUseCase,
        private val widgetRefresher: WidgetRefresher,
        clock: Clock,
    ) : ViewModel() {
        val today: LocalDate = LocalDate.now(clock)

        private val loadAttempt = MutableStateFlow(0)

        // Per-date state cache; entries are cold while unsubscribed (WhileSubscribed), so an
        // off-screen page costs only the map slot. Accessed from composition (main thread).
        private val dayStates = mutableMapOf<LocalDate, StateFlow<DayUiState>>()

        /** The live UI state for [date]; each pager page collects exactly its own date. */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun uiStateFor(date: LocalDate): StateFlow<DayUiState> =
            dayStates.getOrPut(date) {
                loadAttempt
                    .flatMapLatest {
                        getDayReadings(date)
                            .map<DayReadings, DayUiState> { it.toUiState() }
                            // The loader throws on a missing/invalid asset (a build defect, gate-
                            // verified) — but a release build must degrade, not crash (D-S4-3).
                            .catch { emit(DayUiState.LoadFailed(date)) }
                    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayUiState.Loading)
            }

        // Per-month completion cache for the date-picker indicators (S8); same lifecycle
        // policy as dayStates. A failure degrades to "no indicators", never a crash.
        private val monthStates = mutableMapOf<YearMonth, StateFlow<Map<LocalDate, DayCompletion>>>()

        /** Live per-day completion for [month]; the date-picker grid collects exactly its displayed month. */
        fun monthCompletionFor(month: YearMonth): StateFlow<Map<LocalDate, DayCompletion>> =
            monthStates.getOrPut(month) {
                getMonthCompletion(month)
                    .catch { emit(emptyMap()) }
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
            }

        private val openUrlChannel = Channel<String>(Channel.BUFFERED)

        /** One-shot BLB URLs to open in a Custom Tab; collect exactly once from the UI. */
        val openUrlEvents: Flow<String> = openUrlChannel.receiveAsFlow()

        /** Toggles [reading] for the *displayed* [date] — never implicitly "today" (D-S5-3). */
        fun onToggleReading(
            date: LocalDate,
            reading: ReadingStatus,
        ) {
            viewModelScope.launch {
                toggleReading(date, reading.portion.stream, markRead = !reading.isRead)
                // Keep the home-screen widget's completion state consistent (ESpec §7).
                widgetRefresher.refreshTodayWidget()
            }
        }

        /** One tap: marks all three when [dayComplete] is false, unmarks all when true (D-S4-4). */
        fun onMarkWholeDay(
            date: LocalDate,
            dayComplete: Boolean,
        ) {
            viewModelScope.launch {
                markWholeDay(date, markRead = !dayComplete)
                widgetRefresher.refreshTodayWidget()
            }
        }

        fun onReadingTapped(portion: Portion) {
            viewModelScope.launch { openUrlChannel.send(openReference(portion)) }
        }

        fun onRetry() {
            loadAttempt.value += 1
        }

        private fun DayReadings.toUiState(): DayUiState =
            when (this) {
                is DayReadings.Scheduled -> DayUiState.Scheduled(date, readings, dayComplete)
                is DayReadings.NoScheduledReadings -> DayUiState.NoScheduledReadings(date)
            }
    }

package com.jpillion.dailyreadingplanner.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.domain.CompleteReadingDestinationPromptUseCase
import com.jpillion.dailyreadingplanner.domain.CompleteTrackingStartPromptUseCase
import com.jpillion.dailyreadingplanner.domain.CompleteUpgradeNoteUseCase
import com.jpillion.dailyreadingplanner.domain.GetDayReadingsUseCase
import com.jpillion.dailyreadingplanner.domain.GetMonthCompletionUseCase
import com.jpillion.dailyreadingplanner.domain.GetReadingStatsUseCase
import com.jpillion.dailyreadingplanner.domain.GetYearStripsUseCase
import com.jpillion.dailyreadingplanner.domain.MarkWholeDayUseCase
import com.jpillion.dailyreadingplanner.domain.OpenReferenceUseCase
import com.jpillion.dailyreadingplanner.domain.ResolveReadingDestinationPromptUseCase
import com.jpillion.dailyreadingplanner.domain.ResolveTrackingStartPromptUseCase
import com.jpillion.dailyreadingplanner.domain.ResolveUpgradeNoteUseCase
import com.jpillion.dailyreadingplanner.domain.ToggleReadingUseCase
import com.jpillion.dailyreadingplanner.domain.model.DayCompletion
import com.jpillion.dailyreadingplanner.domain.model.DayReadings
import com.jpillion.dailyreadingplanner.domain.model.ExternalBibleApp
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestination
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestinationMode
import com.jpillion.dailyreadingplanner.domain.model.ReadingStatus
import com.jpillion.dailyreadingplanner.ui.navigation.ReaderHandoff
import com.jpillion.dailyreadingplanner.ui.stats.StatsPanelUiState
import com.jpillion.dailyreadingplanner.widget.WidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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
        private val readerHandoff: ReaderHandoff,
        private val completeTrackingStartPrompt: CompleteTrackingStartPromptUseCase,
        resolveTrackingStartPrompt: ResolveTrackingStartPromptUseCase,
        private val completeReadingDestinationPrompt: CompleteReadingDestinationPromptUseCase,
        private val resolveReadingDestinationPrompt: ResolveReadingDestinationPromptUseCase,
        private val completeUpgradeNote: CompleteUpgradeNoteUseCase,
        private val resolveUpgradeNote: ResolveUpgradeNoteUseCase,
        getReadingStats: GetReadingStatsUseCase,
        getYearStrips: GetYearStripsUseCase,
        settingsRepository: SettingsRepository,
        clock: Clock,
    ) : ViewModel() {
        val today: LocalDate = LocalDate.now(clock)

        private val showTrackingStartPromptState = MutableStateFlow(false)

        /**
         * Whether the one-time first-run tracking-start prompt is showing (S19, D-S19-1/2).
         * Resolved once at creation; flips false the moment an answer (or a dismiss) is
         * persisted and, because the marker is then set, never resolves true again.
         */
        val showTrackingStartPrompt: StateFlow<Boolean> = showTrackingStartPromptState

        private val showReadingDestinationPromptState = MutableStateFlow(false)

        /**
         * Whether the first-run reading-destination question is showing (VD-T7, D-V3-19) — only
         * after the tracking-start prompt is answered/dismissed, and only for a genuinely fresh
         * install. In-app is never a silent default: the choice is persisted only on an answer.
         */
        val showReadingDestinationPrompt: StateFlow<Boolean> = showReadingDestinationPromptState

        private val showUpgradeNoteState = MutableStateFlow(false)

        /**
         * Whether the one-time upgrade note is showing (VD-T10, OQ-2) — for EXISTING users only,
         * and never together with the fresh-install question (mutually exclusive by the
         * has-any-marks split). Informational; the user's external provider is preserved unless
         * they explicitly choose to switch.
         */
        val showUpgradeNote: StateFlow<Boolean> = showUpgradeNoteState

        init {
            viewModelScope.launch {
                val showTracking = resolveTrackingStartPrompt()
                showTrackingStartPromptState.value = showTracking
                // Resolve the two V3 first-run gates up front (a snapshot, like S19). They are
                // surfaced in priority order: tracking-start first, then the fresh-install
                // reading-destination question, then the existing-user upgrade note. The
                // reading-destination question waits until the tracking prompt is gone.
                val showReadingDest = resolveReadingDestinationPrompt()
                if (!showTracking) {
                    showReadingDestinationPromptState.value = showReadingDest
                } else {
                    pendingReadingDestinationPrompt = showReadingDest
                }
                showUpgradeNoteState.value = resolveUpgradeNote()
            }
        }

        // Deferred until the tracking prompt is answered/dismissed (so only one dialog shows).
        private var pendingReadingDestinationPrompt = false

        private fun advancePastTrackingPrompt() {
            if (pendingReadingDestinationPrompt) {
                pendingReadingDestinationPrompt = false
                showReadingDestinationPromptState.value = true
            }
        }

        /** The fresh-install reading-destination answer (VD-T7): persist the mode, never ask again. */
        fun onReadingDestinationChosen(mode: ReadingDestinationMode) {
            showReadingDestinationPromptState.value = false
            viewModelScope.launch { completeReadingDestinationPrompt(mode) }
        }

        /**
         * The reading-destination question was dismissed without answering (back/scrim). Per
         * D-V3-19 this is NOT an answer: hide it for now but persist nothing, so it re-asks on the
         * next launch. In-app is never silently chosen.
         */
        fun onReadingDestinationPromptDismissed() {
            showReadingDestinationPromptState.value = false
        }

        /**
         * The upgrade note was acted on (VD-T10): [switchToInApp] true only if the user tapped
         * "use it now"; dismiss passes false (provider preserved). Either way it never re-shows.
         */
        fun onUpgradeNoteDismissed(switchToInApp: Boolean) {
            showUpgradeNoteState.value = false
            viewModelScope.launch { completeUpgradeNote(switchToInApp) }
        }

        /** The user picked a start date (Jan 1 / today / custom) — persist it, never ask again. */
        fun onTrackingStartChosen(date: LocalDate) {
            showTrackingStartPromptState.value = false
            advancePastTrackingPrompt()
            viewModelScope.launch { completeTrackingStartPrompt(date) }
        }

        /**
         * Dismissed without choosing (back/scrim): apply the Jan-1 fallback silently and never
         * re-show (D-S19-1 — the least-nagging option; identical to the superseded D-S14-1
         * default, so a dismissive user gets exactly the old behavior).
         */
        fun onTrackingStartPromptDismissed() {
            onTrackingStartChosen(LocalDate.of(today.year, 1, 1))
        }

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

        /**
         * The main-screen stats panel (S15, D-S15-4): the live S11 stats derivation plus the
         * streak-visibility gate (D-S15-5), rendered ONCE below the pager — the stats are
         * year-level, identical for every displayed day. `null` until the first emission (the
         * pager simply keeps the full height); a derivation failure degrades to no panel,
         * never a crash.
         */
        val statsPanel: StateFlow<StatsPanelUiState?> =
            combine(
                getReadingStats(),
                getYearStrips(),
                settingsRepository.showStreaks,
            ) { stats, strips, showStreaks ->
                StatsPanelUiState(stats = stats, showStreaks = showStreaks, strips = strips)
                    as StatsPanelUiState?
            }.catch { emit(null) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        /**
         * The user's effective reading destination (Sprint K, D-23-1), surfaced reactively so the
         * reading-tile hint reflects the current setting and updates live when the user changes it
         * in Settings. Two flows: the [ReadingDestinationMode] (in-app vs. external) and, for
         * external mode, the chosen [ExternalBibleApp]. Display copy only — the actual tap-time
         * destination is still resolved by [OpenReferenceUseCase] (which re-reads both axes and
         * applies the MySword-not-installed fallback). Seeds with the historical defaults so the
         * hint never flickers through a wrong destination before the first emission.
         */
        val destinationMode: StateFlow<ReadingDestinationMode> =
            settingsRepository.readingDestinationMode
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadingDestinationMode.DEFAULT)

        val externalApp: StateFlow<ExternalBibleApp> =
            settingsRepository.externalBibleApp
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExternalBibleApp.DEFAULT)

        private val openDestinationChannel = Channel<ReadingDestination>(Channel.BUFFERED)

        /** One-shot resolved reading destinations (D-S15-3); collect exactly once from the UI. */
        val openDestinationEvents: Flow<ReadingDestination> = openDestinationChannel.receiveAsFlow()

        private val openReaderChannel = Channel<Unit>(Channel.BUFFERED)

        /**
         * One-shot signal to switch to the Bible tab and open the pending portion in the in-app
         * reader (VD-T5, D-D-1). The portion itself was published to [ReaderHandoff]; this event
         * just tells the UI to navigate. Web/MySword destinations never raise it.
         */
        val openReaderEvents: Flow<Unit> = openReaderChannel.receiveAsFlow()

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
            viewModelScope.launch {
                when (val destination = openReference(portion)) {
                    is ReadingDestination.InApp -> {
                        // V3 (D-V3-18, D-D-1): the in-app reader is a navigation target, not an OS
                        // launch. Publish the portion to the handoff seam and signal the root to
                        // switch to the Bible tab; the OS-launch channel never sees InApp.
                        readerHandoff.request(destination.portion)
                        openReaderChannel.send(Unit)
                    }
                    is ReadingDestination.Web,
                    is ReadingDestination.MySwordApp,
                    -> openDestinationChannel.send(destination)
                }
            }
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

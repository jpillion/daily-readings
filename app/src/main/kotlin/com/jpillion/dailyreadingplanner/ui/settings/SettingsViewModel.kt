package com.jpillion.dailyreadingplanner.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jpillion.dailyreadingplanner.data.apps.AppInstallChecker
import com.jpillion.dailyreadingplanner.data.plan.ActivePlanRepository
import com.jpillion.dailyreadingplanner.data.plan.PlanRegistry
import com.jpillion.dailyreadingplanner.data.plan.ReadingPlanRepository
import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.domain.ResetYearProgressUseCase
import com.jpillion.dailyreadingplanner.domain.model.ExternalBibleApp
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestination
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestinationMode
import com.jpillion.dailyreadingplanner.domain.model.ThemeMode
import com.jpillion.dailyreadingplanner.reminders.NotificationPermissionChecker
import com.jpillion.dailyreadingplanner.reminders.ReminderScheduler
import com.jpillion.dailyreadingplanner.widget.WidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/**
 * Settings state (FR-9 + S8): the persisted theme selection and text-size scale (both read
 * from and written to the DataStore-backed [SettingsRepository] — the same flows MainActivity
 * themes from, so changes restyle the app live), plus the year-scoped "Reset progress"
 * action (owner decision, S8): clears the current year's marks and refreshes the widget.
 * S10 adds the tracking start date (read + write; null = unset).
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val resetYearProgress: ResetYearProgressUseCase,
        private val widgetRefresher: WidgetRefresher,
        private val reminderScheduler: ReminderScheduler,
        private val refreshPersistentNotification:
            com.jpillion.dailyreadingplanner.domain.RefreshPersistentNotificationUseCase,
        private val notificationPermissionChecker: NotificationPermissionChecker,
        appInstallChecker: AppInstallChecker,
        private val activePlanRepository: ActivePlanRepository,
        private val planRegistry: PlanRegistry,
        private val readingPlanRepository: ReadingPlanRepository,
        clock: Clock,
    ) : ViewModel() {
        /** The year a reset would clear — shown in the confirmation dialog. */
        val currentYear: Int = LocalDate.now(clock).year

        // --- Alt Sprint D (D-ALT-18/19): the reading-plan selector. ---

        /**
         * The available plans (registry order, Bible Companion first/default — D-ALT-18) paired
         * with their display name read from each plan's own descriptor head, plus the active id.
         * Combines the one-shot registry/descriptor load with the live [ActivePlanRepository] so a
         * switch — or one made elsewhere — re-selects the row live. The active id degraded to a
         * known plan is [ActivePlanRepository]'s job; the row shows the matching option's name.
         */
        val planSelector: StateFlow<PlanSelectorUiState> =
            activePlanRepository.activePlanId
                .map { activeId ->
                    PlanSelectorUiState(options = loadPlanOptions(), activeId = activeId)
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = PlanSelectorUiState(),
                )

        private var cachedPlanOptions: List<PlanOption>? = null

        /** The registry's plans with descriptor-sourced names, loaded once and memoized. */
        private suspend fun loadPlanOptions(): List<PlanOption> =
            cachedPlanOptions ?: planRegistry
                .plans()
                .map { PlanOption(id = it.id, name = readingPlanRepository.descriptor(it.id).name) }
                .also { cachedPlanOptions = it }

        private val pendingPlanSwitchState = MutableStateFlow<PendingPlanSwitch?>(null)

        /**
         * A user-initiated switch to a DIFFERENT plan, awaiting the one-time explanation dialog
         * (D-ALT-19). `null` when no switch is pending. Selecting the already-active plan never
         * raises it.
         */
        val pendingPlanSwitch: StateFlow<PendingPlanSwitch?> = pendingPlanSwitchState.asStateFlow()

        /**
         * The user tapped a plan in the selector. Selecting the active plan is a no-op (no dialog,
         * no write); selecting a different plan raises the non-destructive-switch explanation
         * (D-ALT-19) — the write happens only on confirm.
         */
        fun onPlanSelected(id: String) {
            val state = planSelector.value
            if (id == state.activeId) return
            val options = state.options
            val fromName = options.firstOrNull { it.id == state.activeId }?.name.orEmpty()
            val toName = options.firstOrNull { it.id == id }?.name.orEmpty()
            pendingPlanSwitchState.value = PendingPlanSwitch(toId = id, fromName = fromName, toName = toName)
        }

        /**
         * Confirm the pending switch (D-ALT-17/19): write `selected_plan` — which re-emits the
         * whole app's active-plan-scoped flows live — and refresh the home-screen widget so the
         * launcher snaps to the new plan (D-ALT-10). NO progress is copied, merged, or cleared:
         * the switch is a pointer move, non-destructive by construction (per-plan progress).
         */
        fun onPlanSwitchConfirmed() {
            val pending = pendingPlanSwitchState.value ?: return
            pendingPlanSwitchState.value = null
            viewModelScope.launch {
                settingsRepository.setSelectedPlanId(pending.toId)
                // The widget reads the active plan through the same use case; snap it to the new plan.
                widgetRefresher.refreshTodayWidget()
            }
        }

        /** Dismiss the pending switch without switching — writes nothing, the active plan is unchanged. */
        fun onPlanSwitchDismissed() {
            pendingPlanSwitchState.value = null
        }

        val themeMode: StateFlow<ThemeMode> =
            settingsRepository.themeMode.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ThemeMode.SYSTEM,
            )

        val fontScale: StateFlow<Float> =
            settingsRepository.fontScale.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsRepository.DEFAULT_FONT_SCALE,
            )

        /** Tracking start date (S10): null = unset = track everything. */
        val trackingStartDate: StateFlow<LocalDate?> =
            settingsRepository.trackingStartDate.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

        fun onThemeModeSelected(mode: ThemeMode) {
            viewModelScope.launch { settingsRepository.setThemeMode(mode) }
        }

        /**
         * Sprint K (D-23-1): WHERE reading taps go — the in-app reader vs. an external app/site.
         * The segmented toggle in Settings writes this; applies to the very next tap.
         */
        val destinationMode: StateFlow<ReadingDestinationMode> =
            settingsRepository.readingDestinationMode.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ReadingDestinationMode.DEFAULT,
            )

        fun onDestinationModeSelected(mode: ReadingDestinationMode) {
            viewModelScope.launch { settingsRepository.setReadingDestinationMode(mode) }
        }

        /**
         * Sprint K (D-23-1): WHICH external app/site is used when the mode is EXTERNAL (S13).
         * The "My Bible app" dropdown writes this; the choice is remembered while the mode is
         * in-app, so it is offered/shown independently of the toggle.
         */
        val externalBibleApp: StateFlow<ExternalBibleApp> =
            settingsRepository.externalBibleApp.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ExternalBibleApp.DEFAULT,
            )

        fun onExternalBibleAppSelected(app: ExternalBibleApp) {
            viewModelScope.launch { settingsRepository.setExternalBibleApp(app) }
        }

        /**
         * S15 (D-S15-2, spec §4 product rule): MySword is offered only when detectably
         * installed — checked once per Settings entry (the ViewModel is created per route
         * push), so an install made while the app runs shows up on the next visit.
         */
        val mySwordInstalled: Boolean =
            appInstallChecker.isInstalled(ReadingDestination.MYSWORD_PACKAGE)

        /** S15 (D-S15-5): streak visibility — display only, on by default. */
        val showStreaks: StateFlow<Boolean> =
            settingsRepository.showStreaks.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = true,
            )

        fun onShowStreaksToggled(show: Boolean) {
            viewModelScope.launch { settingsRepository.setShowStreaks(show) }
        }

        /** Persists each slider position; the app-wide theme collects the same flow (live preview). */
        fun onFontScaleChanged(scale: Float) {
            viewModelScope.launch { settingsRepository.setFontScale(scale) }
        }

        /** Persists the picked tracking start date; null clears it (S10). Never touches marks. */
        fun onTrackingStartChanged(date: LocalDate?) {
            viewModelScope.launch { settingsRepository.setTrackingStartDate(date) }
        }

        // --- S12: daily reminder (PRD §13.2). ---

        val reminderEnabled: StateFlow<Boolean> =
            settingsRepository.reminderEnabled.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false,
            )

        val reminderTime: StateFlow<LocalTime> =
            settingsRepository.reminderTime.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsRepository.DEFAULT_REMINDER_TIME,
            )

        /** Which toggle is awaiting the POST_NOTIFICATIONS result (S21 — shared prompt). */
        private enum class PendingPermissionFeature { REMINDER, PERSISTENT }

        private var pendingPermissionFeature: PendingPermissionFeature? = null

        private val permissionRequestChannel = Channel<Unit>(Channel.BUFFERED)

        /** One-shot "launch the POST_NOTIFICATIONS prompt" events for the Route (R-REM-7). */
        val permissionRequests: Flow<Unit> = permissionRequestChannel.receiveAsFlow()

        private val showPermissionRationaleState = MutableStateFlow(false)

        /** True after a denial: the toggle stays off and a brief explanation shows (R-REM-7). */
        val showPermissionRationale: StateFlow<Boolean> = showPermissionRationaleState.asStateFlow()

        /**
         * The toggle (R-REM-1/2/7): enabling without notification permission requests it
         * instead of persisting — the setting only ever reflects reality. Disabling cancels
         * the alarm immediately.
         */
        fun onReminderToggled(enabled: Boolean) {
            viewModelScope.launch {
                if (enabled && !notificationPermissionChecker.hasNotificationPermission()) {
                    pendingPermissionFeature = PendingPermissionFeature.REMINDER
                    permissionRequestChannel.send(Unit)
                    return@launch
                }
                persistReminderEnabled(enabled)
            }
        }

        // --- S21: persistent (ongoing) tray notification. ---

        val persistentNotificationEnabled: StateFlow<Boolean> =
            settingsRepository.persistentNotificationEnabled.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false,
            )

        /**
         * The toggle (S21, D-S21-5, off by default): enabling without notification permission
         * requests it (shared S12 prompt) instead of persisting. Disabling cancels the alarm and
         * dismisses the notification immediately.
         */
        fun onPersistentNotificationToggled(enabled: Boolean) {
            viewModelScope.launch {
                if (enabled && !notificationPermissionChecker.hasNotificationPermission()) {
                    pendingPermissionFeature = PendingPermissionFeature.PERSISTENT
                    permissionRequestChannel.send(Unit)
                    return@launch
                }
                persistPersistentEnabled(enabled)
            }
        }

        private suspend fun persistPersistentEnabled(enabled: Boolean) {
            settingsRepository.setPersistentNotificationEnabled(enabled)
            // The use case reads the freshly-persisted flag and does the right thing for both
            // states: enabled → post today's readings + arm the 01:00 alarm; disabled → dismiss
            // the notification + cancel the alarm. One home for the rule (no double-action).
            refreshPersistentNotification()
        }

        /** Result of the system permission prompt: granted → enable; denied → explain, stay off. */
        fun onNotificationPermissionResult(granted: Boolean) {
            val feature = pendingPermissionFeature
            pendingPermissionFeature = null
            viewModelScope.launch {
                if (granted) {
                    when (feature) {
                        PendingPermissionFeature.PERSISTENT -> persistPersistentEnabled(true)
                        else -> persistReminderEnabled(true)
                    }
                } else {
                    showPermissionRationaleState.value = true
                }
            }
        }

        fun onPermissionRationaleDismissed() {
            showPermissionRationaleState.value = false
        }

        /** Persists the picked time; an enabled reminder is rescheduled to the new time at once. */
        fun onReminderTimeChanged(time: LocalTime) {
            viewModelScope.launch {
                settingsRepository.setReminderTime(time)
                if (settingsRepository.reminderEnabled.first()) {
                    reminderScheduler.scheduleReminder(time)
                }
            }
        }

        private suspend fun persistReminderEnabled(enabled: Boolean) {
            settingsRepository.setReminderEnabled(enabled)
            if (enabled) {
                reminderScheduler.scheduleReminder(settingsRepository.reminderTime.first())
            } else {
                reminderScheduler.cancelReminder()
            }
        }

        /** Only ever called from the confirmation dialog's positive action (S8). */
        fun onResetProgressConfirmed() {
            viewModelScope.launch {
                resetYearProgress()
                // The widget shows today's completion — a reset must not leave it stale (ESpec §7).
                widgetRefresher.refreshTodayWidget()
            }
        }
    }

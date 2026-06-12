package com.jpillion.dailyreadingplanner.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jpillion.dailyreadingplanner.data.apps.AppInstallChecker
import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.domain.ResetYearProgressUseCase
import com.jpillion.dailyreadingplanner.domain.model.BibleProvider
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestination
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
        private val notificationPermissionChecker: NotificationPermissionChecker,
        appInstallChecker: AppInstallChecker,
        clock: Clock,
    ) : ViewModel() {
        /** The year a reset would clear — shown in the confirmation dialog. */
        val currentYear: Int = LocalDate.now(clock).year

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

        /** S13: the KJV destination reading taps open; applies to the very next tap. */
        val bibleProvider: StateFlow<BibleProvider> =
            settingsRepository.bibleProvider.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = BibleProvider.DEFAULT,
            )

        fun onBibleProviderSelected(provider: BibleProvider) {
            viewModelScope.launch { settingsRepository.setBibleProvider(provider) }
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
                    permissionRequestChannel.send(Unit)
                    return@launch
                }
                persistReminderEnabled(enabled)
            }
        }

        /** Result of the system permission prompt: granted → enable; denied → explain, stay off. */
        fun onNotificationPermissionResult(granted: Boolean) {
            viewModelScope.launch {
                if (granted) {
                    persistReminderEnabled(true)
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

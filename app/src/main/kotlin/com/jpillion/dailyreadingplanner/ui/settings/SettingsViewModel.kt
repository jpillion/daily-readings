package com.jpillion.dailyreadingplanner.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.domain.ResetYearProgressUseCase
import com.jpillion.dailyreadingplanner.domain.model.ThemeMode
import com.jpillion.dailyreadingplanner.widget.WidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
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

        /** Persists each slider position; the app-wide theme collects the same flow (live preview). */
        fun onFontScaleChanged(scale: Float) {
            viewModelScope.launch { settingsRepository.setFontScale(scale) }
        }

        /** Persists the picked tracking start date; null clears it (S10). Never touches marks. */
        fun onTrackingStartChanged(date: LocalDate?) {
            viewModelScope.launch { settingsRepository.setTrackingStartDate(date) }
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

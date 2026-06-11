package com.jpillion.dailyreadingplanner.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Activity-scoped theme state (FR-9, ESpec §5.5): exposes the persisted [ThemeMode] so
 * [MainActivity][com.jpillion.dailyreadingplanner.MainActivity] can drive
 * [DailyReadingPlannerTheme] live. Defaults to [ThemeMode.SYSTEM] until DataStore emits.
 */
@HiltViewModel
class ThemeViewModel
    @Inject
    constructor(
        themeRepository: SettingsRepository,
    ) : ViewModel() {
        val themeMode: StateFlow<ThemeMode> =
            themeRepository.themeMode.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ThemeMode.SYSTEM,
            )

        /** The persisted Settings text-size factor (S8); 1.0 until DataStore emits. */
        val fontScale: StateFlow<Float> =
            themeRepository.fontScale.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsRepository.DEFAULT_FONT_SCALE,
            )
    }

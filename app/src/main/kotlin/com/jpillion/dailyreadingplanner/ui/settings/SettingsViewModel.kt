package com.jpillion.dailyreadingplanner.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jpillion.dailyreadingplanner.data.prefs.ThemeRepository
import com.jpillion.dailyreadingplanner.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Settings state (FR-9): the persisted theme selection, read from and written to the
 * DataStore-backed [ThemeRepository]. The selected value here is the same flow MainActivity
 * themes from, so a selection restyles the app live.
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val themeRepository: ThemeRepository,
    ) : ViewModel() {
        val themeMode: StateFlow<ThemeMode> =
            themeRepository.themeMode.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ThemeMode.SYSTEM,
            )

        fun onThemeModeSelected(mode: ThemeMode) {
            viewModelScope.launch { themeRepository.setThemeMode(mode) }
        }
    }

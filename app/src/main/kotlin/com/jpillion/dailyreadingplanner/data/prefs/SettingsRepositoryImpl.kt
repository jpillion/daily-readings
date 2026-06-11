package com.jpillion.dailyreadingplanner.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jpillion.dailyreadingplanner.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class SettingsRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : SettingsRepository {
        override val themeMode: Flow<ThemeMode> =
            dataStore.data.map { preferences ->
                preferences[THEME_MODE_KEY]?.let { stored ->
                    ThemeMode.entries.firstOrNull { it.name == stored }
                } ?: ThemeMode.SYSTEM
            }

        override val fontScale: Flow<Float> =
            dataStore.data.map { preferences ->
                (preferences[FONT_SCALE_KEY] ?: SettingsRepository.DEFAULT_FONT_SCALE)
                    .coerceIn(SettingsRepository.MIN_FONT_SCALE, SettingsRepository.MAX_FONT_SCALE)
            }

        /** Stored as epoch day (timezone-free, matches the progress PK); absent key => null. */
        override val trackingStartDate: Flow<LocalDate?> =
            dataStore.data.map { preferences ->
                preferences[TRACKING_START_EPOCH_DAY_KEY]?.let(LocalDate::ofEpochDay)
            }

        override val trackingStartInitialized: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[TRACKING_START_INITIALIZED_KEY] ?: false
            }

        override suspend fun setThemeMode(mode: ThemeMode) {
            dataStore.edit { preferences -> preferences[THEME_MODE_KEY] = mode.name }
        }

        override suspend fun setFontScale(scale: Float) {
            dataStore.edit { preferences ->
                preferences[FONT_SCALE_KEY] =
                    scale.coerceIn(SettingsRepository.MIN_FONT_SCALE, SettingsRepository.MAX_FONT_SCALE)
            }
        }

        override suspend fun setTrackingStartDate(date: LocalDate?) {
            dataStore.edit { preferences ->
                if (date == null) {
                    preferences.remove(TRACKING_START_EPOCH_DAY_KEY)
                } else {
                    preferences[TRACKING_START_EPOCH_DAY_KEY] = date.toEpochDay()
                }
            }
        }

        override suspend fun markTrackingStartInitialized() {
            dataStore.edit { preferences -> preferences[TRACKING_START_INITIALIZED_KEY] = true }
        }

        private companion object {
            val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
            val FONT_SCALE_KEY = floatPreferencesKey("font_scale")
            val TRACKING_START_EPOCH_DAY_KEY = longPreferencesKey("tracking_start_epoch_day")
            val TRACKING_START_INITIALIZED_KEY = booleanPreferencesKey("tracking_start_initialized")
        }
    }

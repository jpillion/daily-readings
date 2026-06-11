package com.jpillion.dailyreadingplanner.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jpillion.dailyreadingplanner.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ThemeRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : ThemeRepository {
        override val themeMode: Flow<ThemeMode> =
            dataStore.data.map { preferences ->
                preferences[THEME_MODE_KEY]?.let { stored ->
                    ThemeMode.entries.firstOrNull { it.name == stored }
                } ?: ThemeMode.SYSTEM
            }

        override val fontScale: Flow<Float> =
            dataStore.data.map { preferences ->
                (preferences[FONT_SCALE_KEY] ?: ThemeRepository.DEFAULT_FONT_SCALE)
                    .coerceIn(ThemeRepository.MIN_FONT_SCALE, ThemeRepository.MAX_FONT_SCALE)
            }

        override suspend fun setThemeMode(mode: ThemeMode) {
            dataStore.edit { preferences -> preferences[THEME_MODE_KEY] = mode.name }
        }

        override suspend fun setFontScale(scale: Float) {
            dataStore.edit { preferences ->
                preferences[FONT_SCALE_KEY] =
                    scale.coerceIn(ThemeRepository.MIN_FONT_SCALE, ThemeRepository.MAX_FONT_SCALE)
            }
        }

        private companion object {
            val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
            val FONT_SCALE_KEY = floatPreferencesKey("font_scale")
        }
    }

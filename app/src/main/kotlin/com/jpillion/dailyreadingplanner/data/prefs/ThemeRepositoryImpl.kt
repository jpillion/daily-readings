package com.jpillion.dailyreadingplanner.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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

        override suspend fun setThemeMode(mode: ThemeMode) {
            dataStore.edit { preferences -> preferences[THEME_MODE_KEY] = mode.name }
        }

        private companion object {
            val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        }
    }

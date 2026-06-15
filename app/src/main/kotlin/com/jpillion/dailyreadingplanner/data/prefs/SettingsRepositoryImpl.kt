package com.jpillion.dailyreadingplanner.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jpillion.dailyreadingplanner.domain.model.BibleProvider
import com.jpillion.dailyreadingplanner.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime
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

        override val reminderEnabled: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[REMINDER_ENABLED_KEY] ?: false
            }

        /** Stored as minute-of-day; out-of-range values degrade to the default, never crash. */
        override val reminderTime: Flow<LocalTime> =
            dataStore.data.map { preferences ->
                preferences[REMINDER_MINUTE_OF_DAY_KEY]
                    ?.takeIf { it in 0 until MINUTES_PER_DAY }
                    ?.let { LocalTime.ofSecondOfDay(it * 60L) }
                    ?: SettingsRepository.DEFAULT_REMINDER_TIME
            }

        override suspend fun setReminderEnabled(enabled: Boolean) {
            dataStore.edit { preferences -> preferences[REMINDER_ENABLED_KEY] = enabled }
        }

        override suspend fun setReminderTime(time: LocalTime) {
            dataStore.edit { preferences ->
                preferences[REMINDER_MINUTE_OF_DAY_KEY] = time.hour * 60 + time.minute
            }
        }

        /** Stored as the enum name; [BibleProvider.fromStored] absorbs unknown/corrupt ids. */
        override val bibleProvider: Flow<BibleProvider> =
            dataStore.data.map { preferences ->
                BibleProvider.fromStored(preferences[BIBLE_PROVIDER_KEY])
            }

        override suspend fun setBibleProvider(provider: BibleProvider) {
            dataStore.edit { preferences -> preferences[BIBLE_PROVIDER_KEY] = provider.name }
        }

        override val readingDestinationPromptCompleted: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[READING_DESTINATION_PROMPT_COMPLETED_KEY] ?: false
            }

        override suspend fun markReadingDestinationPromptCompleted() {
            dataStore.edit { preferences -> preferences[READING_DESTINATION_PROMPT_COMPLETED_KEY] = true }
        }

        override val upgradeNoteShown: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[UPGRADE_NOTE_SHOWN_KEY] ?: false
            }

        override suspend fun markUpgradeNoteShown() {
            dataStore.edit { preferences -> preferences[UPGRADE_NOTE_SHOWN_KEY] = true }
        }

        /**
         * S18 (owner, supersedes the S15 default): absent key = false — streaks are opt-in.
         * A user who ever toggled the switch keeps their stored value (normal DataStore
         * behavior); only never-touched installs see the new default.
         */
        override val showStreaks: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[SHOW_STREAKS_KEY] ?: false
            }

        override suspend fun setShowStreaks(show: Boolean) {
            dataStore.edit { preferences -> preferences[SHOW_STREAKS_KEY] = show }
        }

        private companion object {
            val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
            val BIBLE_PROVIDER_KEY = stringPreferencesKey("bible_provider")
            val FONT_SCALE_KEY = floatPreferencesKey("font_scale")
            val TRACKING_START_EPOCH_DAY_KEY = longPreferencesKey("tracking_start_epoch_day")
            val TRACKING_START_INITIALIZED_KEY = booleanPreferencesKey("tracking_start_initialized")
            val SHOW_STREAKS_KEY = booleanPreferencesKey("show_streaks")
            val READING_DESTINATION_PROMPT_COMPLETED_KEY = booleanPreferencesKey("reading_destination_prompt_completed")
            val UPGRADE_NOTE_SHOWN_KEY = booleanPreferencesKey("upgrade_note_shown")
            val REMINDER_ENABLED_KEY = booleanPreferencesKey("reminder_enabled")
            val REMINDER_MINUTE_OF_DAY_KEY = intPreferencesKey("reminder_minute_of_day")
            const val MINUTES_PER_DAY = 24 * 60
        }
    }

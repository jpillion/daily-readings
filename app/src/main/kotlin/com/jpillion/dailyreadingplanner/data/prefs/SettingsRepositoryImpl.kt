package com.jpillion.dailyreadingplanner.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jpillion.dailyreadingplanner.domain.model.ExternalBibleApp
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestinationMode
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

        /**
         * S22 (owner, amends D-S22-5): absent key = true — the persistent notification is ON by
         * default. A user who explicitly toggled it keeps their stored value (normal DataStore
         * behavior); only never-touched installs see the new on-by-default. The launch-time
         * POST_NOTIFICATIONS request (see ShouldRequestNotificationPermissionOnLaunchUseCase)
         * makes on-by-default actually post on a fresh API 33+ install.
         */
        override val persistentNotificationEnabled: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PERSISTENT_NOTIFICATION_ENABLED_KEY] ?: true
            }

        override suspend fun setPersistentNotificationEnabled(enabled: Boolean) {
            dataStore.edit { preferences -> preferences[PERSISTENT_NOTIFICATION_ENABLED_KEY] = enabled }
        }

        /**
         * Sprint K (D-23-1) destination mode. New key `reading_destination_mode`; when ABSENT,
         * migrate from the legacy `bible_provider`: a stored "IN_APP" (the old in-app member) ⇒
         * [ReadingDestinationMode.IN_APP]; anything else, including absent, ⇒
         * [ReadingDestinationMode.EXTERNAL] (the historical BLB-in-browser default). Once the new
         * key is written, the legacy value no longer influences the mode. A corrupt new-key value
         * degrades to [ReadingDestinationMode.DEFAULT].
         */
        override val readingDestinationMode: Flow<ReadingDestinationMode> =
            dataStore.data.map { preferences ->
                val stored = preferences[READING_DESTINATION_MODE_KEY]
                if (stored != null) {
                    ReadingDestinationMode.fromStored(stored)
                } else if (preferences[BIBLE_PROVIDER_KEY] == LEGACY_IN_APP_VALUE) {
                    ReadingDestinationMode.IN_APP
                } else {
                    ReadingDestinationMode.EXTERNAL
                }
            }

        override suspend fun setReadingDestinationMode(mode: ReadingDestinationMode) {
            dataStore.edit { preferences -> preferences[READING_DESTINATION_MODE_KEY] = mode.name }
        }

        /**
         * Sprint K (D-23-1) external app, on the legacy `bible_provider` key. The four external
         * names round-trip unchanged from before Sprint K (R-V3-4). The legacy in-app value
         * ("IN_APP") is no longer an [ExternalBibleApp] member, so [ExternalBibleApp.fromStored]
         * degrades it to the default (BLB) — exactly the external app a legacy in-app user falls
         * back to. Unknown/corrupt ids likewise degrade to the default.
         */
        override val externalBibleApp: Flow<ExternalBibleApp> =
            dataStore.data.map { preferences ->
                ExternalBibleApp.fromStored(preferences[BIBLE_PROVIDER_KEY])
            }

        override suspend fun setExternalBibleApp(app: ExternalBibleApp) {
            dataStore.edit { preferences -> preferences[BIBLE_PROVIDER_KEY] = app.name }
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
            val READING_DESTINATION_MODE_KEY = stringPreferencesKey("reading_destination_mode")

            /** The pre-Sprint-K `bible_provider` value that meant "read in the app". */
            const val LEGACY_IN_APP_VALUE = "IN_APP"
            val FONT_SCALE_KEY = floatPreferencesKey("font_scale")
            val TRACKING_START_EPOCH_DAY_KEY = longPreferencesKey("tracking_start_epoch_day")
            val TRACKING_START_INITIALIZED_KEY = booleanPreferencesKey("tracking_start_initialized")
            val SHOW_STREAKS_KEY = booleanPreferencesKey("show_streaks")
            val READING_DESTINATION_PROMPT_COMPLETED_KEY = booleanPreferencesKey("reading_destination_prompt_completed")
            val UPGRADE_NOTE_SHOWN_KEY = booleanPreferencesKey("upgrade_note_shown")
            val REMINDER_ENABLED_KEY = booleanPreferencesKey("reminder_enabled")
            val REMINDER_MINUTE_OF_DAY_KEY = intPreferencesKey("reminder_minute_of_day")
            val PERSISTENT_NOTIFICATION_ENABLED_KEY = booleanPreferencesKey("persistent_notification_enabled")
            const val MINUTES_PER_DAY = 24 * 60
        }
    }

package com.jpillion.dailyreadingplanner.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.domain.model.ExternalBibleApp
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestinationMode
import com.jpillion.dailyreadingplanner.domain.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/** S3-T6: theme preference persists via DataStore; default and bad values degrade to SYSTEM. */
class SettingsRepositoryImplTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.createDataStore(scope: CoroutineScope): DataStore<Preferences> {
        val file = File(tmp.root, "settings.preferences_pb")
        return PreferenceDataStoreFactory.create(scope = scope) { file }
    }

    private fun themeTest(block: suspend (SettingsRepositoryImpl, DataStore<Preferences>) -> Unit) =
        runTest {
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
            val dataStore = createDataStore(scope)
            try {
                block(SettingsRepositoryImpl(dataStore), dataStore)
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun `defaults to system when nothing is stored`() =
        themeTest { repository, _ ->
            assertThat(repository.themeMode.first()).isEqualTo(ThemeMode.SYSTEM)
        }

    @Test
    fun `set theme mode persists and is observable`() =
        themeTest { repository, _ ->
            repository.setThemeMode(ThemeMode.DARK)
            assertThat(repository.themeMode.first()).isEqualTo(ThemeMode.DARK)
            repository.setThemeMode(ThemeMode.LIGHT)
            assertThat(repository.themeMode.first()).isEqualTo(ThemeMode.LIGHT)
        }

    @Test
    fun `an unrecognized stored value degrades to system instead of crashing`() =
        themeTest { repository, dataStore ->
            dataStore.edit { it[stringPreferencesKey("theme_mode")] = "SOLARIZED" }
            assertThat(repository.themeMode.first()).isEqualTo(ThemeMode.SYSTEM)
        }

    @Test
    fun `font scale defaults to 1x when nothing is stored`() =
        themeTest { repository, _ ->
            assertThat(repository.fontScale.first()).isEqualTo(SettingsRepository.DEFAULT_FONT_SCALE)
        }

    @Test
    fun `set font scale persists and is observable`() =
        themeTest { repository, _ ->
            repository.setFontScale(1.25f)
            assertThat(repository.fontScale.first()).isEqualTo(1.25f)
        }

    @Test
    fun `font scale writes and reads are clamped to the supported range`() =
        themeTest { repository, dataStore ->
            repository.setFontScale(9f)
            assertThat(repository.fontScale.first()).isEqualTo(SettingsRepository.MAX_FONT_SCALE)
            repository.setFontScale(0.1f)
            assertThat(repository.fontScale.first()).isEqualTo(SettingsRepository.MIN_FONT_SCALE)
            // A bad stored value (e.g. from a future version) degrades to the range, not a crash.
            dataStore.edit { it[floatPreferencesKey("font_scale")] = 42f }
            assertThat(repository.fontScale.first()).isEqualTo(SettingsRepository.MAX_FONT_SCALE)
        }

    // --- S10: tracking start date persistence (spec §4). ---

    @Test
    fun `tracking start defaults to null when nothing is stored`() =
        themeTest { repository, _ ->
            assertThat(repository.trackingStartDate.first()).isNull()
        }

    @Test
    fun `tracking start round-trips, including a leap day - no epoch-day off-by-one`() =
        themeTest { repository, _ ->
            repository.setTrackingStartDate(LocalDate.of(2026, 6, 10))
            assertThat(repository.trackingStartDate.first()).isEqualTo(LocalDate.of(2026, 6, 10))
            repository.setTrackingStartDate(LocalDate.of(2028, 2, 29))
            assertThat(repository.trackingStartDate.first()).isEqualTo(LocalDate.of(2028, 2, 29))
        }

    @Test
    fun `clearing the tracking start removes the key and reads back null`() =
        themeTest { repository, _ ->
            repository.setTrackingStartDate(LocalDate.of(2026, 6, 10))
            repository.setTrackingStartDate(null)
            assertThat(repository.trackingStartDate.first()).isNull()
        }

    @Test
    fun `initialized marker defaults false and sticks once marked`() =
        themeTest { repository, _ ->
            assertThat(repository.trackingStartInitialized.first()).isFalse()
            repository.markTrackingStartInitialized()
            assertThat(repository.trackingStartInitialized.first()).isTrue()
        }

    // --- S12: reminder persistence (R-REM-1/2). ---

    @Test
    fun `reminder defaults to off with the 8am default time when nothing is stored`() =
        themeTest { repository, _ ->
            assertThat(repository.reminderEnabled.first()).isFalse()
            assertThat(repository.reminderTime.first()).isEqualTo(LocalTime.of(8, 0))
        }

    @Test
    fun `reminder enabled round-trips both ways`() =
        themeTest { repository, _ ->
            repository.setReminderEnabled(true)
            assertThat(repository.reminderEnabled.first()).isTrue()
            repository.setReminderEnabled(false)
            assertThat(repository.reminderEnabled.first()).isFalse()
        }

    @Test
    fun `reminder time round-trips, including midnight and 23-59 boundaries`() =
        themeTest { repository, _ ->
            repository.setReminderTime(LocalTime.of(21, 15))
            assertThat(repository.reminderTime.first()).isEqualTo(LocalTime.of(21, 15))
            repository.setReminderTime(LocalTime.MIDNIGHT)
            assertThat(repository.reminderTime.first()).isEqualTo(LocalTime.MIDNIGHT)
            repository.setReminderTime(LocalTime.of(23, 59))
            assertThat(repository.reminderTime.first()).isEqualTo(LocalTime.of(23, 59))
        }

    @Test
    fun `a corrupt stored reminder minute degrades to the default instead of crashing`() =
        themeTest { repository, dataStore ->
            dataStore.edit { it[intPreferencesKey("reminder_minute_of_day")] = 4_000 }
            assertThat(repository.reminderTime.first()).isEqualTo(LocalTime.of(8, 0))
            dataStore.edit { it[intPreferencesKey("reminder_minute_of_day")] = -1 }
            assertThat(repository.reminderTime.first()).isEqualTo(LocalTime.of(8, 0))
        }

    // --- Sprint K (D-23-1): reading-destination mode + external app + legacy migration. ---

    @Test
    fun `destination mode defaults to external when nothing is stored`() =
        themeTest { repository, _ ->
            // A clean install reads in an external app (the historical BLB-in-browser default).
            assertThat(repository.readingDestinationMode.first())
                .isEqualTo(ReadingDestinationMode.EXTERNAL)
        }

    @Test
    fun `set destination mode persists and is observable`() =
        themeTest { repository, _ ->
            repository.setReadingDestinationMode(ReadingDestinationMode.IN_APP)
            assertThat(repository.readingDestinationMode.first())
                .isEqualTo(ReadingDestinationMode.IN_APP)
            repository.setReadingDestinationMode(ReadingDestinationMode.EXTERNAL)
            assertThat(repository.readingDestinationMode.first())
                .isEqualTo(ReadingDestinationMode.EXTERNAL)
        }

    @Test
    fun `MUTATION legacy IN_APP bible_provider migrates to in-app mode plus BLB external app`() =
        themeTest { repository, dataStore ->
            // A pre-Sprint-K user who chose the in-app reader stored bible_provider == "IN_APP"
            // (no new reading_destination_mode key). They must read IN-APP, and their external
            // app degrades to BLB (the safe default). Mutation target: the legacy-value comparison.
            dataStore.edit { it[stringPreferencesKey("bible_provider")] = "IN_APP" }
            assertThat(repository.readingDestinationMode.first())
                .isEqualTo(ReadingDestinationMode.IN_APP)
            assertThat(repository.externalBibleApp.first()).isEqualTo(ExternalBibleApp.BLB)
        }

    @Test
    fun `legacy external bible_provider migrates to external mode preserving the app`() =
        themeTest { repository, dataStore ->
            // A pre-Sprint-K user on YouVersion keeps YouVersion in external mode — zero behavior
            // change. No new mode key is present; the migration infers EXTERNAL.
            dataStore.edit { it[stringPreferencesKey("bible_provider")] = "YOUVERSION" }
            assertThat(repository.readingDestinationMode.first())
                .isEqualTo(ReadingDestinationMode.EXTERNAL)
            assertThat(repository.externalBibleApp.first()).isEqualTo(ExternalBibleApp.YOUVERSION)
        }

    @Test
    fun `an explicit new mode key wins over the legacy bible_provider value`() =
        themeTest { repository, dataStore ->
            // Once the new key is written, the legacy value no longer drives the mode.
            dataStore.edit { it[stringPreferencesKey("bible_provider")] = "IN_APP" }
            repository.setReadingDestinationMode(ReadingDestinationMode.EXTERNAL)
            assertThat(repository.readingDestinationMode.first())
                .isEqualTo(ReadingDestinationMode.EXTERNAL)
        }

    @Test
    fun `external app defaults to blue letter bible when nothing is stored`() =
        themeTest { repository, _ ->
            assertThat(repository.externalBibleApp.first()).isEqualTo(ExternalBibleApp.BLB)
        }

    @Test
    fun `set external app persists and is observable`() =
        themeTest { repository, _ ->
            repository.setExternalBibleApp(ExternalBibleApp.YOUVERSION)
            assertThat(repository.externalBibleApp.first()).isEqualTo(ExternalBibleApp.YOUVERSION)
            repository.setExternalBibleApp(ExternalBibleApp.BIBLE_GATEWAY)
            assertThat(repository.externalBibleApp.first()).isEqualTo(ExternalBibleApp.BIBLE_GATEWAY)
        }

    @Test
    fun `an unrecognized stored external app id degrades to the default instead of crashing`() =
        themeTest { repository, dataStore ->
            dataStore.edit { it[stringPreferencesKey("bible_provider")] = "ESV_DOT_ORG" }
            assertThat(repository.externalBibleApp.first()).isEqualTo(ExternalBibleApp.BLB)
        }

    @Test
    fun `setting external app does not touch the destination mode and vice versa`() =
        themeTest { repository, _ ->
            // The two axes are independent: switching the external app while in-app mode is set
            // does not flip the mode back to external (the remembered-app guarantee).
            repository.setReadingDestinationMode(ReadingDestinationMode.IN_APP)
            repository.setExternalBibleApp(ExternalBibleApp.YOUVERSION)
            assertThat(repository.readingDestinationMode.first())
                .isEqualTo(ReadingDestinationMode.IN_APP)
            assertThat(repository.externalBibleApp.first()).isEqualTo(ExternalBibleApp.YOUVERSION)
        }

    // --- S15: streak visibility (D-S15-5); default flipped off in S18 (owner). ---

    @Test
    fun `show streaks defaults to false when nothing is stored - streaks are opt-in`() =
        themeTest { repository, _ ->
            assertThat(repository.showStreaks.first()).isFalse()
        }

    @Test
    fun `an explicitly stored true survives the S18 default flip`() =
        themeTest { repository, dataStore ->
            // A user who toggled streaks on before S18 keeps them on.
            dataStore.edit { it[booleanPreferencesKey("show_streaks")] = true }
            assertThat(repository.showStreaks.first()).isTrue()
        }

    @Test
    fun `show streaks round-trips both ways`() =
        themeTest { repository, _ ->
            repository.setShowStreaks(false)
            assertThat(repository.showStreaks.first()).isFalse()
            repository.setShowStreaks(true)
            assertThat(repository.showStreaks.first()).isTrue()
        }

    // --- S21 persistent notification; default flipped ON in S22 (owner, amends D-S22-5). ---

    @Test
    fun `persistent notification defaults to on when nothing is stored - on-by-default`() =
        themeTest { repository, _ ->
            // Mutation target: the absent-key default (true). A fresh install is on by default
            // so the day's readings sit in the tray without the user opting in.
            assertThat(repository.persistentNotificationEnabled.first()).isTrue()
        }

    @Test
    fun `an explicitly stored false survives the S22 default flip`() =
        themeTest { repository, dataStore ->
            // Mutation target: a deliberate OFF must not be overridden by the new on-by-default.
            // A user who turned it off keeps it off across launches.
            dataStore.edit { it[booleanPreferencesKey("persistent_notification_enabled")] = false }
            assertThat(repository.persistentNotificationEnabled.first()).isFalse()
        }

    @Test
    fun `persistent notification round-trips both ways`() =
        themeTest { repository, _ ->
            repository.setPersistentNotificationEnabled(false)
            assertThat(repository.persistentNotificationEnabled.first()).isFalse()
            repository.setPersistentNotificationEnabled(true)
            assertThat(repository.persistentNotificationEnabled.first()).isTrue()
        }
}

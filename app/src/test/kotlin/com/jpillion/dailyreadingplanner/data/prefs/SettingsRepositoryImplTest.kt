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
import com.jpillion.dailyreadingplanner.domain.model.BibleProvider
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

    // --- S13: bible provider. ---

    @Test
    fun `bible provider defaults to blue letter bible when nothing is stored`() =
        themeTest { repository, _ ->
            assertThat(repository.bibleProvider.first()).isEqualTo(BibleProvider.BLB)
        }

    @Test
    fun `set bible provider persists and is observable`() =
        themeTest { repository, _ ->
            repository.setBibleProvider(BibleProvider.YOUVERSION)
            assertThat(repository.bibleProvider.first()).isEqualTo(BibleProvider.YOUVERSION)
            repository.setBibleProvider(BibleProvider.BIBLE_GATEWAY)
            assertThat(repository.bibleProvider.first()).isEqualTo(BibleProvider.BIBLE_GATEWAY)
        }

    @Test
    fun `an unrecognized stored provider id degrades to the default instead of crashing`() =
        themeTest { repository, dataStore ->
            dataStore.edit { it[stringPreferencesKey("bible_provider")] = "ESV_DOT_ORG" }
            assertThat(repository.bibleProvider.first()).isEqualTo(BibleProvider.BLB)
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
}

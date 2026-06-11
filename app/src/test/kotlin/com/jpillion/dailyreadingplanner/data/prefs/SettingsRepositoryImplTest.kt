package com.jpillion.dailyreadingplanner.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
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
}

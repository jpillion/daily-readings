package com.jpillion.dailyreadingplanner.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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

/** S3-T6: theme preference persists via DataStore; default and bad values degrade to SYSTEM. */
class ThemeRepositoryImplTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.createDataStore(scope: CoroutineScope): DataStore<Preferences> {
        val file = File(tmp.root, "settings.preferences_pb")
        return PreferenceDataStoreFactory.create(scope = scope) { file }
    }

    private fun themeTest(block: suspend (ThemeRepositoryImpl, DataStore<Preferences>) -> Unit) =
        runTest {
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
            val dataStore = createDataStore(scope)
            try {
                block(ThemeRepositoryImpl(dataStore), dataStore)
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
}

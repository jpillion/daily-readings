package com.jpillion.dailyreadingplanner.ui.settings

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.domain.model.ThemeMode
import com.jpillion.dailyreadingplanner.testing.FakeThemeRepository
import com.jpillion.dailyreadingplanner.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeThemeRepository()
    private val viewModel by lazy { SettingsViewModel(repository) }

    @Test
    fun `initial state is SYSTEM before the repository emits`() {
        assertThat(viewModel.themeMode.value).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test
    fun `persisted mode flows into state`() =
        runTest {
            repository.stored.value = ThemeMode.DARK
            viewModel.themeMode.test {
                var state = awaitItem()
                while (state != ThemeMode.DARK) state = awaitItem()
                assertThat(state).isEqualTo(ThemeMode.DARK)
            }
        }

    @Test
    fun `selecting a mode persists it and updates state`() =
        runTest {
            viewModel.themeMode.test {
                awaitItem() // SYSTEM
                viewModel.onThemeModeSelected(ThemeMode.DARK)
                assertThat(awaitItem()).isEqualTo(ThemeMode.DARK)
                viewModel.onThemeModeSelected(ThemeMode.LIGHT)
                assertThat(awaitItem()).isEqualTo(ThemeMode.LIGHT)
            }
            assertThat(repository.setCalls).containsExactly(ThemeMode.DARK, ThemeMode.LIGHT).inOrder()
        }
}

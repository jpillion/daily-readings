package com.jpillion.dailyreadingplanner.ui.settings

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.domain.FakeProgressRepository
import com.jpillion.dailyreadingplanner.domain.ResetYearProgressUseCase
import com.jpillion.dailyreadingplanner.domain.model.Stream
import com.jpillion.dailyreadingplanner.domain.model.ThemeMode
import com.jpillion.dailyreadingplanner.testing.FakeThemeRepository
import com.jpillion.dailyreadingplanner.testing.FakeWidgetRefresher
import com.jpillion.dailyreadingplanner.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeThemeRepository()
    private val progress = FakeProgressRepository()
    private val widgetRefresher = FakeWidgetRefresher()
    private val clock = Clock.fixed(Instant.parse("2026-06-10T12:00:00Z"), ZoneOffset.UTC)
    private val viewModel by lazy {
        SettingsViewModel(
            themeRepository = repository,
            resetYearProgress = ResetYearProgressUseCase(progress, clock),
            widgetRefresher = widgetRefresher,
            clock = clock,
        )
    }

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

    @Test
    fun `font scale changes persist and update state`() =
        runTest {
            viewModel.fontScale.test {
                awaitItem() // 1.0 default
                viewModel.onFontScaleChanged(1.25f)
                assertThat(awaitItem()).isEqualTo(1.25f)
            }
            assertThat(repository.fontScaleCalls).containsExactly(1.25f)
        }

    @Test
    fun `current year comes from the clock`() {
        assertThat(viewModel.currentYear).isEqualTo(2026)
    }

    @Test
    fun `confirmed reset clears the current year and refreshes the widget`() =
        runTest {
            progress.setWholeDay(LocalDate.of(2026, 6, 1), isRead = true)
            progress.setRead(LocalDate.of(2025, 6, 1), Stream.NEW_TESTAMENT, isRead = true)
            viewModel.onResetProgressConfirmed()
            assertThat(progress.clearYearCalls).containsExactly(2026)
            assertThat(progress.marksFor(LocalDate.of(2026, 6, 1))).isEmpty()
            assertThat(progress.marksFor(LocalDate.of(2025, 6, 1))).containsExactly(Stream.NEW_TESTAMENT)
            assertThat(widgetRefresher.refreshCount).isEqualTo(1)
        }
}

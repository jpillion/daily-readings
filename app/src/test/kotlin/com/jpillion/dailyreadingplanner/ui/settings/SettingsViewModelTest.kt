package com.jpillion.dailyreadingplanner.ui.settings

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.domain.FakeProgressRepository
import com.jpillion.dailyreadingplanner.domain.ResetYearProgressUseCase
import com.jpillion.dailyreadingplanner.domain.model.Stream
import com.jpillion.dailyreadingplanner.domain.model.ThemeMode
import com.jpillion.dailyreadingplanner.testing.FakeNotificationPermissionChecker
import com.jpillion.dailyreadingplanner.testing.FakeReminderScheduler
import com.jpillion.dailyreadingplanner.testing.FakeSettingsRepository
import com.jpillion.dailyreadingplanner.testing.FakeWidgetRefresher
import com.jpillion.dailyreadingplanner.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSettingsRepository()
    private val progress = FakeProgressRepository()
    private val widgetRefresher = FakeWidgetRefresher()
    private val reminderScheduler = FakeReminderScheduler()
    private val permissionChecker = FakeNotificationPermissionChecker(granted = true)
    private val clock = Clock.fixed(Instant.parse("2026-06-10T12:00:00Z"), ZoneOffset.UTC)
    private val viewModel by lazy {
        SettingsViewModel(
            settingsRepository = repository,
            resetYearProgress = ResetYearProgressUseCase(progress, clock),
            widgetRefresher = widgetRefresher,
            reminderScheduler = reminderScheduler,
            notificationPermissionChecker = permissionChecker,
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

    // --- S10: tracking start date. ---

    @Test
    fun `tracking start defaults to null and reflects the persisted value`() =
        runTest {
            viewModel.trackingStartDate.test {
                assertThat(awaitItem()).isNull()
                repository.storedTrackingStartDate.value = LocalDate.of(2026, 6, 3)
                assertThat(awaitItem()).isEqualTo(LocalDate.of(2026, 6, 3))
            }
        }

    @Test
    fun `changing the tracking start writes through, and clearing writes null`() =
        runTest {
            viewModel.trackingStartDate.test {
                awaitItem() // null default
                viewModel.onTrackingStartChanged(LocalDate.of(2026, 6, 3))
                assertThat(awaitItem()).isEqualTo(LocalDate.of(2026, 6, 3))
                viewModel.onTrackingStartChanged(null)
                assertThat(awaitItem()).isNull()
            }
            assertThat(repository.trackingStartCalls)
                .containsExactly(LocalDate.of(2026, 6, 3), null)
                .inOrder()
        }

    @Test
    fun `reset and tracking start are independent - neither touches the other`() =
        runTest {
            // Reset clears marks but never the tracking start date (spec §7).
            repository.storedTrackingStartDate.value = LocalDate.of(2026, 6, 3)
            viewModel.onResetProgressConfirmed()
            assertThat(repository.trackingStartCalls).isEmpty()
            assertThat(repository.storedTrackingStartDate.value).isEqualTo(LocalDate.of(2026, 6, 3))
            // Changing the start date never clears marks.
            progress.setWholeDay(LocalDate.of(2026, 5, 1), isRead = true)
            val clearsBefore = progress.clearYearCalls.size
            viewModel.onTrackingStartChanged(LocalDate.of(2026, 1, 1))
            assertThat(progress.clearYearCalls).hasSize(clearsBefore)
            assertThat(progress.marksFor(LocalDate.of(2026, 5, 1))).isNotEmpty()
        }

    // --- S12: daily reminder (R-REM-1/2/7). ---

    @Test
    fun `reminder defaults off with the 8am time`() {
        assertThat(viewModel.reminderEnabled.value).isFalse()
        assertThat(viewModel.reminderTime.value).isEqualTo(LocalTime.of(8, 0))
    }

    @Test
    fun `enabling with permission persists and arms the alarm at the stored time`() =
        runTest {
            repository.storedReminderTime.value = LocalTime.of(7, 15)
            viewModel.onReminderToggled(true)
            assertThat(repository.reminderEnabledCalls).containsExactly(true)
            assertThat(reminderScheduler.scheduledTimes).containsExactly(LocalTime.of(7, 15))
        }

    @Test
    fun `disabling persists off and cancels the alarm`() =
        runTest {
            repository.storedReminderEnabled.value = true
            viewModel.onReminderToggled(false)
            assertThat(repository.reminderEnabledCalls).containsExactly(false)
            assertThat(reminderScheduler.cancelCount).isEqualTo(1)
            assertThat(reminderScheduler.scheduledTimes).isEmpty()
        }

    @Test
    fun `enabling without permission requests it instead of persisting - toggle reflects reality`() =
        runTest {
            permissionChecker.granted = false
            viewModel.permissionRequests.test {
                viewModel.onReminderToggled(true)
                awaitItem()
                assertThat(repository.reminderEnabledCalls).isEmpty()
                assertThat(reminderScheduler.scheduledTimes).isEmpty()
            }
        }

    @Test
    fun `permission granted from the prompt enables and arms`() =
        runTest {
            viewModel.onNotificationPermissionResult(granted = true)
            assertThat(repository.reminderEnabledCalls).containsExactly(true)
            assertThat(reminderScheduler.scheduledTimes).hasSize(1)
            assertThat(viewModel.showPermissionRationale.value).isFalse()
        }

    @Test
    fun `permission denied keeps the setting off and shows the explanation until dismissed`() =
        runTest {
            viewModel.onNotificationPermissionResult(granted = false)
            assertThat(repository.reminderEnabledCalls).isEmpty()
            assertThat(reminderScheduler.scheduledTimes).isEmpty()
            assertThat(viewModel.showPermissionRationale.value).isTrue()
            viewModel.onPermissionRationaleDismissed()
            assertThat(viewModel.showPermissionRationale.value).isFalse()
        }

    @Test
    fun `changing the time while enabled persists and reschedules to the new time`() =
        runTest {
            repository.storedReminderEnabled.value = true
            viewModel.onReminderTimeChanged(LocalTime.of(21, 0))
            assertThat(repository.reminderTimeCalls).containsExactly(LocalTime.of(21, 0))
            assertThat(reminderScheduler.scheduledTimes).containsExactly(LocalTime.of(21, 0))
        }

    @Test
    fun `changing the time while disabled persists but never arms an alarm`() =
        runTest {
            viewModel.onReminderTimeChanged(LocalTime.of(21, 0))
            assertThat(repository.reminderTimeCalls).containsExactly(LocalTime.of(21, 0))
            assertThat(reminderScheduler.scheduledTimes).isEmpty()
        }
}

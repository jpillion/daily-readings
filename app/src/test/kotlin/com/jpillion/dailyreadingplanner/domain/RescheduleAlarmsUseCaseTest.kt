package com.jpillion.dailyreadingplanner.domain

import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.core.date.ScheduleDateResolver
import com.jpillion.dailyreadingplanner.testing.FakePersistentNotifier
import com.jpillion.dailyreadingplanner.testing.FakeReminderScheduler
import com.jpillion.dailyreadingplanner.testing.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** S12: boot/app-launch re-arming (R-REM-8) — alarms always mirror persisted state. */
class RescheduleAlarmsUseCaseTest {
    private val settings = FakeSettingsRepository()
    private val scheduler = FakeReminderScheduler()
    private val persistentNotifier = FakePersistentNotifier()
    private val progress = FakeProgressRepository()
    private val refreshPersistent =
        RefreshPersistentNotificationUseCase(
            settingsRepository = settings,
            getDayReadings =
                GetDayReadingsUseCase(
                    resolver = ScheduleDateResolver(),
                    planRepository = FakeReadingPlanRepository(),
                    progressRepository = progress,
                ),
            notifier = persistentNotifier,
            scheduler = scheduler,
            clock =
                Clock.fixed(
                    LocalDate.of(2026, 6, 10).atStartOfDay().toInstant(ZoneOffset.UTC),
                    ZoneOffset.UTC,
                ),
        )
    private val useCase = RescheduleAlarmsUseCase(settings, scheduler, refreshPersistent)

    @Test
    fun `midnight refresh is re-armed even with the reminder off - independent per D-S12-2`() =
        runTest {
            settings.storedReminderEnabled.value = false
            useCase()
            assertThat(scheduler.midnightRefreshCount).isEqualTo(1)
        }

    @Test
    fun `reminder enabled re-arms the reminder at the persisted time`() =
        runTest {
            settings.storedReminderEnabled.value = true
            settings.storedReminderTime.value = LocalTime.of(20, 15)
            useCase()
            assertThat(scheduler.scheduledTimes).containsExactly(LocalTime.of(20, 15))
            assertThat(scheduler.cancelCount).isEqualTo(0)
        }

    @Test
    fun `reminder disabled cancels instead of scheduling - a stale alarm cannot outlive a disable`() =
        runTest {
            settings.storedReminderEnabled.value = false
            useCase()
            assertThat(scheduler.scheduledTimes).isEmpty()
            assertThat(scheduler.cancelCount).isEqualTo(1)
        }

    @Test
    fun `persistent enabled re-arms the 01-00 alarm and posts the current notification`() =
        runTest {
            settings.storedPersistentEnabled.value = true
            useCase()
            assertThat(scheduler.persistentRefreshCount).isEqualTo(1)
            assertThat(persistentNotifier.shown).hasSize(1)
        }

    @Test
    fun `persistent disabled cancels the alarm and dismisses the notification`() =
        runTest {
            // Mutation target: drop the persistent disable-cancel and a stale notification survives.
            settings.storedPersistentEnabled.value = false
            useCase()
            assertThat(scheduler.cancelPersistentCount).isEqualTo(1)
            assertThat(persistentNotifier.cancelCount).isEqualTo(1)
            assertThat(persistentNotifier.shown).isEmpty()
        }
}

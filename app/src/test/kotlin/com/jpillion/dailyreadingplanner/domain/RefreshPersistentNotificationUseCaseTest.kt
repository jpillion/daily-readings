package com.jpillion.dailyreadingplanner.domain

import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.core.date.ScheduleDateResolver
import com.jpillion.dailyreadingplanner.domain.model.DayReadings
import com.jpillion.dailyreadingplanner.testing.FakePersistentNotifier
import com.jpillion.dailyreadingplanner.testing.FakeReminderScheduler
import com.jpillion.dailyreadingplanner.testing.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * S21: the persistent-notification refresh rules (D-S21-3/4) — post today's readings and
 * re-arm the 01:00 alarm while enabled (no completion suppression — the readings stay shown);
 * Feb 29 posts the no-readings state rather than vanishing; a disabled toggle cancels the
 * notification and its alarm and does not re-arm.
 */
class RefreshPersistentNotificationUseCaseTest {
    private val settings = FakeSettingsRepository()
    private val progress = FakeProgressRepository()
    private val notifier = FakePersistentNotifier()
    private val scheduler = FakeReminderScheduler()

    private fun useCase(today: LocalDate): RefreshPersistentNotificationUseCase =
        RefreshPersistentNotificationUseCase(
            settingsRepository = settings,
            getDayReadings =
                GetDayReadingsUseCase(
                    resolver = ScheduleDateResolver(),
                    planRepository = FakeReadingPlanRepository(),
                    progressRepository = progress,
                    activePlanRepository = FakeActivePlanRepository(),
                ),
            notifier = notifier,
            scheduler = scheduler,
            clock = Clock.fixed(today.atTime(1, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
        )

    private val ordinaryDay = LocalDate.of(2026, 6, 10)
    private val leapDay = LocalDate.of(2028, 2, 29)

    @Test
    fun `enabled - posts today's readings and re-arms the 01-00 alarm`() =
        runTest {
            settings.storedPersistentEnabled.value = true
            useCase(ordinaryDay)()
            assertThat(notifier.shown).hasSize(1)
            assertThat(notifier.shown.single()).isInstanceOf(DayReadings.Scheduled::class.java)
            assertThat(scheduler.persistentRefreshCount).isEqualTo(1)
            assertThat(notifier.cancelCount).isEqualTo(0)
        }

    @Test
    fun `a fully complete day still shows the readings - no suppression unlike the popup`() =
        runTest {
            settings.storedPersistentEnabled.value = true
            progress.setWholeDay(ordinaryDay, listOf(1, 2, 3), isRead = true)
            useCase(ordinaryDay)()
            assertThat(notifier.shown).hasSize(1)
            assertThat(scheduler.persistentRefreshCount).isEqualTo(1)
        }

    @Test
    fun `Feb 29 posts the no-readings state and still re-arms`() =
        runTest {
            // Mutation target: drop the "post regardless of type" path and this fails.
            settings.storedPersistentEnabled.value = true
            useCase(leapDay)()
            assertThat(notifier.shown).hasSize(1)
            assertThat(notifier.shown.single()).isInstanceOf(DayReadings.NoScheduledReadings::class.java)
            assertThat(scheduler.persistentRefreshCount).isEqualTo(1)
        }

    @Test
    fun `disabled - cancels the notification and its alarm, does not re-arm or post`() =
        runTest {
            // Mutation target: drop the disabled gate and this fails (it would post + re-arm).
            settings.storedPersistentEnabled.value = false
            useCase(ordinaryDay)()
            assertThat(notifier.shown).isEmpty()
            assertThat(notifier.cancelCount).isEqualTo(1)
            assertThat(scheduler.cancelPersistentCount).isEqualTo(1)
            assertThat(scheduler.persistentRefreshCount).isEqualTo(0)
        }
}

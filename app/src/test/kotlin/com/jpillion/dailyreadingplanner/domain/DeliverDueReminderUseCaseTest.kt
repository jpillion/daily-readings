package com.jpillion.dailyreadingplanner.domain

import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.core.date.ScheduleDateResolver
import com.jpillion.dailyreadingplanner.testing.FakeReminderNotifier
import com.jpillion.dailyreadingplanner.testing.FakeReminderScheduler
import com.jpillion.dailyreadingplanner.testing.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * S12: the fire-time rules (D-S12-3) — show vs. quietly skip (R-REM-4/5), always re-arm
 * for tomorrow while enabled, and never act on a stale alarm after a disable.
 */
class DeliverDueReminderUseCaseTest {
    private val settings = FakeSettingsRepository()
    private val progress = FakeProgressRepository()
    private val notifier = FakeReminderNotifier()
    private val scheduler = FakeReminderScheduler()

    private fun useCase(today: LocalDate): DeliverDueReminderUseCase =
        DeliverDueReminderUseCase(
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
            clock =
                Clock.fixed(
                    today.atTime(8, 0).toInstant(ZoneOffset.UTC),
                    ZoneOffset.UTC,
                ),
        )

    private val ordinaryDay = LocalDate.of(2026, 6, 10)
    private val leapDay = LocalDate.of(2028, 2, 29)

    @Test
    fun `an incomplete day shows the reminder with today's three portions and re-arms`() =
        runTest {
            settings.storedReminderEnabled.value = true
            settings.storedReminderTime.value = LocalTime.of(8, 0)
            useCase(ordinaryDay)()
            assertThat(notifier.shownPortions).hasSize(1)
            assertThat(notifier.shownPortions.single()).isEqualTo(threePortions)
            assertThat(scheduler.scheduledTimes).containsExactly(LocalTime.of(8, 0))
        }

    @Test
    fun `a partially complete day still reminds - only a fully complete day is quiet`() =
        runTest {
            settings.storedReminderEnabled.value = true
            progress.setRead(
                ordinaryDay,
                3,
                isRead = true,
            )
            useCase(ordinaryDay)()
            assertThat(notifier.shownPortions).hasSize(1)
        }

    @Test
    fun `a complete day is quietly skipped but the alarm is still re-armed for tomorrow`() =
        runTest {
            // R-REM-4 (mutation target M-S12-1: drop the dayComplete guard and this fails).
            settings.storedReminderEnabled.value = true
            settings.storedReminderTime.value = LocalTime.of(21, 30)
            progress.setWholeDay(ordinaryDay, listOf(1, 2, 3), isRead = true)
            useCase(ordinaryDay)()
            assertThat(notifier.shownPortions).isEmpty()
            assertThat(scheduler.scheduledTimes).containsExactly(LocalTime.of(21, 30))
        }

    @Test
    fun `Feb 29 has no scheduled readings - quietly skipped, alarm still re-armed`() =
        runTest {
            // R-REM-5 (mutation target M-S12-2: drop the Scheduled type check and this fails).
            settings.storedReminderEnabled.value = true
            useCase(leapDay)()
            assertThat(notifier.shownPortions).isEmpty()
            assertThat(scheduler.scheduledTimes).hasSize(1)
        }

    @Test
    fun `a stale alarm after disabling does nothing - no notification, no re-arm`() =
        runTest {
            settings.storedReminderEnabled.value = false
            useCase(ordinaryDay)()
            assertThat(notifier.shownPortions).isEmpty()
            assertThat(scheduler.scheduledTimes).isEmpty()
            assertThat(scheduler.cancelCount).isEqualTo(0)
        }

    @Test
    fun `re-arm uses the currently persisted time, not a captured one`() =
        runTest {
            settings.storedReminderEnabled.value = true
            settings.storedReminderTime.value = LocalTime.of(6, 45)
            useCase(ordinaryDay)()
            assertThat(scheduler.scheduledTimes).containsExactly(LocalTime.of(6, 45))
        }
}

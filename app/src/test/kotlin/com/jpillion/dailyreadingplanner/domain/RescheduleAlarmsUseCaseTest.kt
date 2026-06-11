package com.jpillion.dailyreadingplanner.domain

import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.testing.FakeReminderScheduler
import com.jpillion.dailyreadingplanner.testing.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalTime

/** S12: boot/app-launch re-arming (R-REM-8) — alarms always mirror persisted state. */
class RescheduleAlarmsUseCaseTest {
    private val settings = FakeSettingsRepository()
    private val scheduler = FakeReminderScheduler()
    private val useCase = RescheduleAlarmsUseCase(settings, scheduler)

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
}

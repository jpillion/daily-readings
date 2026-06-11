package com.jpillion.dailyreadingplanner.reminders

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * S12 (D-S12-1): the platform edge of the alarm seam — inexact RTC_WAKEUP alarms land at
 * the AlarmTimes-computed instant, rescheduling replaces the standing alarm, cancel clears it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlarmManagerReminderSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarmManager = requireNotNull(context.getSystemService(AlarmManager::class.java))

    // 2026-06-10T07:30Z — before the 08:00 default, so "today" is the expected target.
    private val now = Instant.parse("2026-06-10T07:30:00Z")
    private val scheduler = AlarmManagerReminderScheduler(context, Clock.fixed(now, ZoneOffset.UTC))

    private fun epochMilliAtUtc(
        hour: Int,
        minute: Int,
        dayOfMonth: Int = 10,
    ): Long =
        ZonedDateTime
            .of(2026, 6, dayOfMonth, hour, minute, 0, 0, ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()

    @Test
    fun `scheduleReminder arms one inexact RTC_WAKEUP alarm at the next occurrence`() {
        scheduler.scheduleReminder(LocalTime.of(8, 0))
        val alarm = requireNotNull(shadowOf(alarmManager).peekNextScheduledAlarm())
        assertThat(alarm.type).isEqualTo(AlarmManager.RTC_WAKEUP)
        assertThat(alarm.triggerAtMs).isEqualTo(epochMilliAtUtc(8, 0))
        assertThat(shadowOf(alarmManager).scheduledAlarms).hasSize(1)
    }

    @Test
    fun `rescheduling replaces the standing alarm instead of stacking a second one`() {
        scheduler.scheduleReminder(LocalTime.of(8, 0))
        scheduler.scheduleReminder(LocalTime.of(21, 30))
        val alarms = shadowOf(alarmManager).scheduledAlarms
        assertThat(alarms).hasSize(1)
        assertThat(alarms.single().triggerAtMs).isEqualTo(epochMilliAtUtc(21, 30))
    }

    @Test
    fun `cancelReminder clears the standing reminder alarm`() {
        scheduler.scheduleReminder(LocalTime.of(8, 0))
        scheduler.cancelReminder()
        assertThat(shadowOf(alarmManager).scheduledAlarms).isEmpty()
    }

    @Test
    fun `midnight refresh arms its own alarm at the next local midnight`() {
        scheduler.scheduleMidnightRefresh()
        val alarm = requireNotNull(shadowOf(alarmManager).peekNextScheduledAlarm())
        assertThat(alarm.type).isEqualTo(AlarmManager.RTC_WAKEUP)
        assertThat(alarm.triggerAtMs).isEqualTo(epochMilliAtUtc(0, 0, dayOfMonth = 11))
    }

    @Test
    fun `the reminder and midnight alarms are independent - cancelling one keeps the other`() {
        scheduler.scheduleReminder(LocalTime.of(8, 0))
        scheduler.scheduleMidnightRefresh()
        assertThat(shadowOf(alarmManager).scheduledAlarms).hasSize(2)
        scheduler.cancelReminder()
        val remaining = shadowOf(alarmManager).scheduledAlarms
        assertThat(remaining).hasSize(1)
        assertThat(remaining.single().triggerAtMs).isEqualTo(epochMilliAtUtc(0, 0, dayOfMonth = 11))
    }
}

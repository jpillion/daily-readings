package com.jpillion.dailyreadingplanner.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.jpillion.dailyreadingplanner.platform.DateProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.datetime.LocalTime
import javax.inject.Inject

/**
 * The alarm seam (S12, D-S12-4 — the [com.jpillion.dailyreadingplanner.widget.WidgetRefresher]
 * pattern): domain logic schedules against this interface; only the implementation touches
 * AlarmManager, so suppression/reschedule rules stay JVM-testable.
 */
interface ReminderScheduler {
    /** (Re)schedules the one daily-reminder alarm for the next occurrence of [time]. */
    fun scheduleReminder(time: LocalTime)

    /** Cancels any pending daily-reminder alarm (toggle off / permission revoked). */
    fun cancelReminder()

    /**
     * (Re)schedules the midnight widget-refresh alarm for the next local midnight (FR-23,
     * D-S12-2). Independent of the reminder toggle; idempotent (one standing alarm).
     */
    fun scheduleMidnightRefresh()

    /** (Re)schedules the persistent-notification refresh alarm for the next 01:00 local (S21, D-S21-3). */
    fun schedulePersistentRefresh()

    /** Cancels any pending persistent-notification refresh alarm (toggle off). */
    fun cancelPersistentRefresh()
}

/**
 * Production implementation: inexact `setAndAllowWhileIdle(RTC_WAKEUP)` alarms (D-S12-1) —
 * no SCHEDULE_EXACT_ALARM permission, no Play exact-alarm policy burden; "within a few
 * minutes" satisfies R-REM-8 and is plenty for a midnight rollover. One PendingIntent per
 * action (FLAG_UPDATE_CURRENT), so rescheduling replaces rather than stacks.
 */
class AlarmManagerReminderScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val dateProvider: DateProvider,
    ) : ReminderScheduler {
        private val alarmManager: AlarmManager
            get() = requireNotNull(context.getSystemService(AlarmManager::class.java))

        override fun scheduleReminder(time: LocalTime) {
            val trigger = AlarmTimes.nextOccurrence(dateProvider.now(), dateProvider.timeZone, time)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                trigger.toEpochMilliseconds(),
                reminderIntent(),
            )
        }

        override fun cancelReminder() {
            alarmManager.cancel(reminderIntent())
        }

        override fun scheduleMidnightRefresh() {
            val trigger = AlarmTimes.nextMidnight(dateProvider.now(), dateProvider.timeZone)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                trigger.toEpochMilliseconds(),
                midnightRefreshIntent(),
            )
        }

        override fun schedulePersistentRefresh() {
            val trigger =
                AlarmTimes.nextOccurrence(
                    dateProvider.now(),
                    dateProvider.timeZone,
                    AlarmTimes.PERSISTENT_REFRESH_TIME,
                )
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                trigger.toEpochMilliseconds(),
                persistentRefreshIntent(),
            )
        }

        override fun cancelPersistentRefresh() {
            alarmManager.cancel(persistentRefreshIntent())
        }

        private fun reminderIntent(): PendingIntent =
            broadcast(REQUEST_REMINDER, ReminderAlarmReceiver.ACTION_SHOW_REMINDER)

        private fun midnightRefreshIntent(): PendingIntent =
            broadcast(REQUEST_MIDNIGHT_REFRESH, ReminderAlarmReceiver.ACTION_MIDNIGHT_REFRESH)

        private fun persistentRefreshIntent(): PendingIntent =
            broadcast(REQUEST_PERSISTENT_REFRESH, ReminderAlarmReceiver.ACTION_REFRESH_PERSISTENT)

        private fun broadcast(
            requestCode: Int,
            action: String,
        ): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, ReminderAlarmReceiver::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        private companion object {
            const val REQUEST_REMINDER = 2001
            const val REQUEST_MIDNIGHT_REFRESH = 2002
            const val REQUEST_PERSISTENT_REFRESH = 2004
        }
    }

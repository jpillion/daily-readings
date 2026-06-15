package com.jpillion.dailyreadingplanner.testing

import com.jpillion.dailyreadingplanner.domain.model.DayReadings
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.reminders.NotificationPermissionChecker
import com.jpillion.dailyreadingplanner.reminders.ReminderNotifier
import com.jpillion.dailyreadingplanner.reminders.ReminderScheduler
import java.time.LocalTime

/** Records scheduling calls in order (S12) — order matters for reschedule-rule tests. */
class FakeReminderScheduler : ReminderScheduler {
    val scheduledTimes = mutableListOf<LocalTime>()
    var cancelCount = 0
        private set
    var midnightRefreshCount = 0
        private set
    val callLog = mutableListOf<String>()

    override fun scheduleReminder(time: LocalTime) {
        scheduledTimes += time
        callLog += "scheduleReminder($time)"
    }

    override fun cancelReminder() {
        cancelCount++
        callLog += "cancelReminder"
    }

    override fun scheduleMidnightRefresh() {
        midnightRefreshCount++
        callLog += "scheduleMidnightRefresh"
    }

    var persistentRefreshCount = 0
        private set
    var cancelPersistentCount = 0
        private set

    override fun schedulePersistentRefresh() {
        persistentRefreshCount++
        callLog += "schedulePersistentRefresh"
    }

    override fun cancelPersistentRefresh() {
        cancelPersistentCount++
        callLog += "cancelPersistentRefresh"
    }
}

/** Records every posted reminder's portions (S12). */
class FakeReminderNotifier : ReminderNotifier {
    val shownPortions = mutableListOf<List<Portion>>()

    override fun showTodayReminder(portions: List<Portion>) {
        shownPortions += portions
    }
}

/** Records every persistent-notification post/cancel (S21). */
class FakePersistentNotifier : com.jpillion.dailyreadingplanner.reminders.PersistentNotifier {
    val shown = mutableListOf<DayReadings>()
    var cancelCount = 0
        private set

    override fun showPersistent(day: DayReadings) {
        shown += day
    }

    override fun cancelPersistent() {
        cancelCount++
    }
}

class FakeNotificationPermissionChecker(
    var granted: Boolean = true,
) : NotificationPermissionChecker {
    override fun hasNotificationPermission(): Boolean = granted
}

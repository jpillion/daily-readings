package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.domain.model.DayReadings
import com.jpillion.dailyreadingplanner.reminders.ReminderNotifier
import com.jpillion.dailyreadingplanner.reminders.ReminderScheduler
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Runs when the daily-reminder alarm fires (S12, FR-20/21). Suppression is decided here,
 * at fire time (D-S12-3), against the same engine the UI uses:
 *
 * - reminder disabled (stale alarm after a toggle-off race) → do nothing, don't reschedule;
 * - day already complete (R-REM-4) → quietly skip, no replacement congratulation;
 * - Feb 29, no scheduled readings (R-REM-5) → quietly skip;
 * - otherwise → one notification listing today's three readings (R-REM-3).
 *
 * Whenever the reminder remains enabled the alarm is re-armed for the next occurrence of
 * the chosen time — tomorrow, since "now" is at/after today's occurrence. Only ever about
 * today; never retroactive (R-REM-6).
 */
class DeliverDueReminderUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val getDayReadings: GetDayReadingsUseCase,
        private val notifier: ReminderNotifier,
        private val scheduler: ReminderScheduler,
        private val clock: Clock,
    ) {
        suspend operator fun invoke() {
            if (!settingsRepository.reminderEnabled.first()) return
            val today = LocalDate.now(clock)
            val day = getDayReadings(today).first()
            if (day is DayReadings.Scheduled && !day.dayComplete) {
                notifier.showTodayReminder(day.readings.map { it.portion })
            }
            scheduler.scheduleReminder(settingsRepository.reminderTime.first())
        }
    }

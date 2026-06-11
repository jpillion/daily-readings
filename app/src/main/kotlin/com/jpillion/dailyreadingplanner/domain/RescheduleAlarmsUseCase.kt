package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.reminders.ReminderScheduler
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Re-arms the app's standing alarms from persisted state (S12, R-REM-8): on boot (alarms
 * are cleared by reboot) and on app launch (cheap, idempotent — covers app update and the
 * relaunch after a force-stop). The midnight widget refresh is unconditional (D-S12-2);
 * the reminder alarm mirrors the persisted toggle, cancelling when off so a stale alarm
 * can never outlive a disable.
 */
class RescheduleAlarmsUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val scheduler: ReminderScheduler,
    ) {
        suspend operator fun invoke() {
            scheduler.scheduleMidnightRefresh()
            if (settingsRepository.reminderEnabled.first()) {
                scheduler.scheduleReminder(settingsRepository.reminderTime.first())
            } else {
                scheduler.cancelReminder()
            }
        }
    }

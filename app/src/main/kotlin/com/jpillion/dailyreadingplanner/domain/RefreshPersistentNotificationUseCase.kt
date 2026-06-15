package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.reminders.PersistentNotifier
import com.jpillion.dailyreadingplanner.reminders.ReminderScheduler
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Refreshes the persistent (ongoing) tray notification (S21, D-S21-3/4). Fired by the 01:00
 * alarm, and reusable on boot/launch. Content is decided here, at fire time, against the same
 * [GetDayReadingsUseCase] the UI uses — so the notification always shows *today's* readings:
 *
 * - persistent notification disabled (stale alarm after a toggle-off race) → cancel it, do not
 *   re-arm;
 * - otherwise → post/update the ongoing notification with today's readings (Feb 29 shows the
 *   no-readings line, the notification stays present), then re-arm the next 01:00 alarm.
 *
 * Unlike the popup reminder there is no completion suppression — the owner wants the day's
 * readings always visible in the tray, complete or not.
 */
class RefreshPersistentNotificationUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val getDayReadings: GetDayReadingsUseCase,
        private val notifier: PersistentNotifier,
        private val scheduler: ReminderScheduler,
        private val clock: Clock,
    ) {
        suspend operator fun invoke() {
            if (!settingsRepository.persistentNotificationEnabled.first()) {
                notifier.cancelPersistent()
                scheduler.cancelPersistentRefresh()
                return
            }
            val today = LocalDate.now(clock)
            val day = getDayReadings(today).first()
            notifier.showPersistent(day)
            scheduler.schedulePersistentRefresh()
        }
    }

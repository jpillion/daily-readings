package com.jpillion.dailyreadingplanner.reminders

import com.jpillion.dailyreadingplanner.widget.WidgetRefresher
import javax.inject.Inject

/**
 * Fired by the midnight alarm (FR-23, D-S12-2): snap the widget to the new day, then stand
 * the next midnight alarm back up (D-S12-3: the standing alarm always points at the next
 * occurrence). Independent of the reminder toggle; the 30-min periodic update remains the
 * backstop for the force-stop case (alarms do not survive force-stop).
 */
class MidnightRefreshHandler
    @Inject
    constructor(
        private val widgetRefresher: WidgetRefresher,
        private val scheduler: ReminderScheduler,
    ) {
        suspend operator fun invoke() {
            widgetRefresher.refreshTodayWidget()
            scheduler.scheduleMidnightRefresh()
        }
    }

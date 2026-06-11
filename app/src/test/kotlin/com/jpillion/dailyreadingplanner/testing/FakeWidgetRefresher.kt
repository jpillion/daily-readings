package com.jpillion.dailyreadingplanner.testing

import com.jpillion.dailyreadingplanner.widget.WidgetRefresher

/** Records refresh requests so UI-layer hooks (D-S7-2) can be pinned without Glance. */
class FakeWidgetRefresher : WidgetRefresher {
    var refreshCount = 0
        private set

    override suspend fun refreshTodayWidget() {
        refreshCount++
    }
}

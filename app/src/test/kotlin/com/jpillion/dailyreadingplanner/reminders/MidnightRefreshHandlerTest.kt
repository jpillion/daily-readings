package com.jpillion.dailyreadingplanner.reminders

import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.testing.FakeReminderScheduler
import com.jpillion.dailyreadingplanner.testing.FakeWidgetRefresher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** S12 (FR-23): the midnight alarm snaps the widget to the new day, then re-arms itself. */
class MidnightRefreshHandlerTest {
    @Test
    fun `refreshes the widget and re-arms the next midnight alarm`() =
        runTest {
            val widgetRefresher = FakeWidgetRefresher()
            val scheduler = FakeReminderScheduler()
            MidnightRefreshHandler(widgetRefresher, scheduler)()
            assertThat(widgetRefresher.refreshCount).isEqualTo(1)
            assertThat(scheduler.midnightRefreshCount).isEqualTo(1)
            assertThat(scheduler.scheduledTimes).isEmpty()
        }
}

package com.jpillion.dailyreadingplanner.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jpillion.dailyreadingplanner.domain.DeliverDueReminderUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Thin alarm target (S12, D-S12-4): all decisions live in the injected use cases; this
 * only routes the action and bridges the broadcast to coroutines via goAsync (the work is
 * a few DataStore/Room reads + a notify — well inside the broadcast budget).
 */
@AndroidEntryPoint
class ReminderAlarmReceiver : BroadcastReceiver() {
    @Inject
    lateinit var deliverDueReminder: DeliverDueReminderUseCase

    @Inject
    lateinit var midnightRefresh: MidnightRefreshHandler

    @Inject
    lateinit var refreshPersistent: com.jpillion.dailyreadingplanner.domain.RefreshPersistentNotificationUseCase

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                when (action) {
                    ACTION_SHOW_REMINDER -> deliverDueReminder()
                    ACTION_MIDNIGHT_REFRESH -> midnightRefresh()
                    ACTION_REFRESH_PERSISTENT -> refreshPersistent()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_SHOW_REMINDER = "com.jpillion.dailyreadingplanner.action.SHOW_REMINDER"
        const val ACTION_MIDNIGHT_REFRESH = "com.jpillion.dailyreadingplanner.action.MIDNIGHT_REFRESH"
        const val ACTION_REFRESH_PERSISTENT = "com.jpillion.dailyreadingplanner.action.REFRESH_PERSISTENT"
    }
}

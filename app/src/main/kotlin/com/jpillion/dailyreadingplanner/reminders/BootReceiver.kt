package com.jpillion.dailyreadingplanner.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jpillion.dailyreadingplanner.domain.RescheduleAlarmsUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Reboot wipes AlarmManager state; this re-arms the standing alarms from persisted
 * settings (R-REM-8 + D-S12-2). BOOT_COMPLETED is a protected broadcast (only the system
 * can send it), so the receiver stays non-exported.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject
    lateinit var rescheduleAlarms: RescheduleAlarmsUseCase

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                rescheduleAlarms()
            } finally {
                pendingResult.finish()
            }
        }
    }
}

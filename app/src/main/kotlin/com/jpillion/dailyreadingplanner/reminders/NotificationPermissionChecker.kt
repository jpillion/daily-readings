package com.jpillion.dailyreadingplanner.reminders

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Permission seam (S12, D-S12-6): the ViewModel gates the reminder toggle on this, so the
 * R-REM-7 enable/deny flow is unit-testable with a fake. Below API 33 there is no runtime
 * notification permission — always granted.
 */
interface NotificationPermissionChecker {
    fun hasNotificationPermission(): Boolean
}

class AndroidNotificationPermissionChecker
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : NotificationPermissionChecker {
        override fun hasNotificationPermission(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }

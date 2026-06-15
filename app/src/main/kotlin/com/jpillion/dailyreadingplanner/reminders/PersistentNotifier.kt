package com.jpillion.dailyreadingplanner.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jpillion.dailyreadingplanner.MainActivity
import com.jpillion.dailyreadingplanner.R
import com.jpillion.dailyreadingplanner.domain.model.DayReadings
import com.jpillion.dailyreadingplanner.ui.day.ReadingFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The persistent-notification seam (S21, parallels [ReminderNotifier]): domain decides
 * *whether* the ongoing notification should exist; this decides *how* it looks. Kept behind
 * an interface so content selection (Feb 29) is JVM-testable.
 */
interface PersistentNotifier {
    /** Posts/updates the one ongoing notification for [day] (today). */
    fun showPersistent(day: DayReadings)

    /** Removes the ongoing notification (toggle off). */
    fun cancelPersistent()
}

/**
 * Production implementation (S21, D-S21-1/2/4): one silent, ongoing notification on its own
 * IMPORTANCE_LOW channel — `setOngoing(true)` (non-dismissible, no foreground service needed,
 * D-S21-2), PRIORITY_MAX + CATEGORY_STATUS to bias ranking high within a low channel (Android
 * owns final placement — "close to the top" is best-effort). BigTextStyle lists today's three
 * readings; Feb 29 shows the no-readings line rather than vanishing. Tap opens the app on today.
 * Respectful copy per PRD §13.0: no emoji, no urgency, no streaks.
 */
class SystemPersistentNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : PersistentNotifier {
        override fun showPersistent(day: DayReadings) {
            // Belt-and-braces (mirrors S12): the toggle gates permission, but a system-settings
            // revoke can race a pending alarm — degrade to silence, never crash.
            val granted =
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            if (!granted) return
            ensureChannel()
            val body = persistentBody(context, day)
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification_reminder)
                    .setContentTitle(context.getString(R.string.persistent_notification_title))
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setContentIntent(contentIntent())
                    .setOngoing(true)
                    .setSilent(true)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setShowWhen(false)
                    .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }

        override fun cancelPersistent() {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }

        private fun ensureChannel() {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.persistent_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = context.getString(R.string.persistent_channel_description) }
            NotificationManagerCompat.from(context).createNotificationChannel(channel)
        }

        private fun contentIntent(): PendingIntent =
            PendingIntent.getActivity(
                context,
                REQUEST_OPEN_APP,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        companion object {
            const val CHANNEL_ID = "persistent_readings"
            const val NOTIFICATION_ID = 3002
            private const val REQUEST_OPEN_APP = 2005

            /**
             * "Genesis 1–2 · Psalm 1–2 · Matthew 1–2" for a scheduled day; the no-readings line
             * for Feb 29 (the notification stays present either way). Pure → pinned by a JVM test.
             */
            fun persistentBody(
                context: Context,
                day: DayReadings,
            ): String =
                when (day) {
                    is DayReadings.Scheduled ->
                        day.readings.joinToString(" · ") { ReadingFormatter.format(it.portion) }
                    is DayReadings.NoScheduledReadings ->
                        context.getString(R.string.persistent_no_readings_body)
                }
        }
    }

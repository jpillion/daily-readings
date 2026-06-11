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
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.ui.day.ReadingFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The notification seam (S12, D-S12-4): domain logic decides *whether* to remind; this
 * decides *how*. Kept behind an interface so the suppression rules are JVM-testable.
 */
interface ReminderNotifier {
    /** Posts the one daily reminder listing today's [portions] (R-REM-3). */
    fun showTodayReminder(portions: List<Portion>)
}

/**
 * Production implementation: one plain notification — title "Today's readings", body =
 * the three references in the established collapsed format, tap opens the app on today
 * (MainActivity always lands on today's page). Respectful copy per PRD §13.0: no emoji,
 * no urgency, no streaks. Channel registered lazily (minSdk 26 = channels always exist).
 */
class SystemReminderNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ReminderNotifier {
        override fun showTodayReminder(portions: List<Portion>) {
            // Belt-and-braces permission check (R-REM-7): the toggle flow should make this
            // unreachable without permission, but the user can revoke from system settings
            // while an alarm is pending — degrade to silence, never crash.
            val granted =
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            if (!granted) return
            ensureChannel()
            val body = reminderBody(portions)
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification_reminder)
                    .setContentTitle(context.getString(R.string.reminder_notification_title))
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setContentIntent(contentIntent())
                    .setAutoCancel(true)
                    .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }

        private fun ensureChannel() {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.reminder_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.reminder_channel_description) }
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
            const val CHANNEL_ID = "daily_reminder"
            const val NOTIFICATION_ID = 3001
            private const val REQUEST_OPEN_APP = 2003

            /**
             * "Genesis 1–2 · Psalm 1–2 · Matthew 1–2" — the same collapsed references the
             * app renders (R-REM-3). Pure, so the copy is pinned by a plain JVM test.
             */
            fun reminderBody(portions: List<Portion>): String =
                portions.joinToString(" · ") { ReadingFormatter.format(it) }
        }
    }

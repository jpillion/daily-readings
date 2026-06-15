package com.jpillion.dailyreadingplanner.reminders

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.domain.model.DayReadings
import com.jpillion.dailyreadingplanner.domain.model.ReadingStatus
import com.jpillion.dailyreadingplanner.domain.threePortions
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * S21 (D-S21-1/2/4): the ongoing notification — silent low-importance channel, non-dismissible,
 * the day's references as the body, the no-readings line on Feb 29, tap opens the app, and
 * silence when permission is missing. No foreground service.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemPersistentNotifierTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notificationManager =
        requireNotNull(context.getSystemService(NotificationManager::class.java))
    private val notifier = SystemPersistentNotifier(context)

    private val scheduledDay =
        DayReadings.Scheduled(
            date = LocalDate.of(2026, 1, 1),
            readings = threePortions.map { ReadingStatus(it, isRead = false) },
            dayComplete = false,
        )
    private val febTwentyNine = DayReadings.NoScheduledReadings(LocalDate.of(2028, 2, 29))

    private fun grant() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun `posts one ongoing notification with the collapsed references`() {
        grant()
        notifier.showPersistent(scheduledDay)
        val all = shadowOf(notificationManager).allNotifications
        assertThat(all).hasSize(1)
        val n = all.single()
        assertThat(n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            .isEqualTo("Genesis 1–2 · Psalms 1–2 · Matthew 1–2")
        assertThat(n.flags and Notification.FLAG_ONGOING_EVENT).isEqualTo(Notification.FLAG_ONGOING_EVENT)
    }

    @Test
    fun `registers a low-importance channel separate from the reminder channel`() {
        grant()
        notifier.showPersistent(scheduledDay)
        val channel =
            shadowOf(notificationManager)
                .notificationChannels
                .map { it as android.app.NotificationChannel }
                .single { it.id == SystemPersistentNotifier.CHANNEL_ID }
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_LOW)
        assertThat(SystemPersistentNotifier.CHANNEL_ID).isNotEqualTo(SystemReminderNotifier.CHANNEL_ID)
    }

    @Test
    fun `Feb 29 shows the no-readings body and the notification stays present`() {
        grant()
        notifier.showPersistent(febTwentyNine)
        val all = shadowOf(notificationManager).allNotifications
        assertThat(all).hasSize(1)
        assertThat(
            all
                .single()
                .extras
                .getCharSequence(Notification.EXTRA_TEXT)
                .toString(),
        ).isEqualTo("No readings scheduled today")
    }

    @Test
    fun `tapping opens MainActivity`() {
        grant()
        notifier.showPersistent(scheduledDay)
        val shadowIntent = shadowOf(shadowOf(notificationManager).allNotifications.single().contentIntent)
        assertThat(shadowIntent.savedIntent.component?.className)
            .isEqualTo("com.jpillion.dailyreadingplanner.MainActivity")
    }

    @Test
    fun `without permission it degrades to silence`() {
        notifier.showPersistent(scheduledDay)
        assertThat(shadowOf(notificationManager).allNotifications).isEmpty()
    }

    @Test
    fun `cancel removes the ongoing notification`() {
        grant()
        notifier.showPersistent(scheduledDay)
        notifier.cancelPersistent()
        assertThat(shadowOf(notificationManager).allNotifications).isEmpty()
    }
}

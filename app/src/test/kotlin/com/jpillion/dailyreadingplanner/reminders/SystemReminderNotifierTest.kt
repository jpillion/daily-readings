package com.jpillion.dailyreadingplanner.reminders

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.domain.portion
import com.jpillion.dailyreadingplanner.domain.threePortions
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * S12 (R-REM-3/7): the notification itself — respectful fixed copy, the day's references
 * as the body, a registered channel, and silence when permission is missing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemReminderNotifierTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notificationManager =
        requireNotNull(context.getSystemService(NotificationManager::class.java))
    private val notifier = SystemReminderNotifier(context)

    private fun grantNotificationPermission() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun `posts one notification titled Today's readings with the collapsed references`() {
        grantNotificationPermission()
        notifier.showTodayReminder(threePortions)
        val all = shadowOf(notificationManager).allNotifications
        assertThat(all).hasSize(1)
        val extras = all.single().extras
        assertThat(extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            .isEqualTo("Today's readings")
        assertThat(extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            .isEqualTo("Genesis 1–2 · Psalms 1–2 · Matthew 1–2")
    }

    @Test
    fun `registers the daily reminder channel`() {
        grantNotificationPermission()
        notifier.showTodayReminder(threePortions)
        val channel =
            shadowOf(notificationManager).notificationChannels.single()
                as android.app.NotificationChannel
        assertThat(channel.id).isEqualTo(SystemReminderNotifier.CHANNEL_ID)
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_DEFAULT)
    }

    @Test
    fun `without notification permission it degrades to silence, never a crash`() {
        // R-REM-7 belt-and-braces: permission can be revoked while an alarm is pending.
        notifier.showTodayReminder(threePortions)
        assertThat(shadowOf(notificationManager).allNotifications).isEmpty()
    }

    @Test
    fun `tapping the notification opens the app - content intent targets MainActivity`() {
        grantNotificationPermission()
        notifier.showTodayReminder(threePortions)
        val notification = shadowOf(notificationManager).allNotifications.single()
        val shadowIntent = shadowOf(notification.contentIntent)
        assertThat(shadowIntent.savedIntent.component?.className)
            .isEqualTo("com.jpillion.dailyreadingplanner.MainActivity")
        assertThat(notification.flags and Notification.FLAG_AUTO_CANCEL)
            .isEqualTo(Notification.FLAG_AUTO_CANCEL)
    }

    @Test
    fun `body copy handles the multi-book day - 2 John and 3 John stay one reading`() {
        // Jun 19 / Dec 19 (Sprint 3): one portion, two single-chapter books.
        val multiBook =
            listOf(
                portion(1, "Deuteronomy" to 33, "Deuteronomy" to 34),
                portion(2, "Isaiah" to 60),
                portion(3, "2 John" to 1, "3 John" to 1),
            )
        assertThat(SystemReminderNotifier.reminderBody(multiBook))
            .isEqualTo("Deuteronomy 33–34 · Isaiah 60 · 2 John 1; 3 John 1")
    }
}

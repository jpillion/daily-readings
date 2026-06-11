package com.jpillion.dailyreadingplanner.di

import com.jpillion.dailyreadingplanner.reminders.AlarmManagerReminderScheduler
import com.jpillion.dailyreadingplanner.reminders.AndroidNotificationPermissionChecker
import com.jpillion.dailyreadingplanner.reminders.NotificationPermissionChecker
import com.jpillion.dailyreadingplanner.reminders.ReminderNotifier
import com.jpillion.dailyreadingplanner.reminders.ReminderScheduler
import com.jpillion.dailyreadingplanner.reminders.SystemReminderNotifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Reminder seams (S12, D-S12-4/6) — logic depends on interfaces, never platform impls. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderModule {
    @Binds
    @Singleton
    abstract fun bindReminderScheduler(impl: AlarmManagerReminderScheduler): ReminderScheduler

    @Binds
    @Singleton
    abstract fun bindReminderNotifier(impl: SystemReminderNotifier): ReminderNotifier

    @Binds
    @Singleton
    abstract fun bindNotificationPermissionChecker(
        impl: AndroidNotificationPermissionChecker,
    ): NotificationPermissionChecker
}

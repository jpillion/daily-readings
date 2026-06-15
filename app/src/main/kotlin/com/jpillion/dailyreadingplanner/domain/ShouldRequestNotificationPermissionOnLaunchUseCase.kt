package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.reminders.NotificationPermissionChecker
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Launch-time decision (S22, amends D-S22-5): now that the persistent tray notification is ON
 * by default, a fresh API 33+ install has the setting enabled but no POST_NOTIFICATIONS grant —
 * so the notification can't actually post. On launch, if the persistent notification is enabled
 * and the runtime notification permission is not yet granted, the app requests it once.
 *
 * - On API < 33 [NotificationPermissionChecker.hasNotificationPermission] is always true, so this
 *   returns false and no prompt is shown — the notification just posts (handled by the
 *   reschedule/refresh hook).
 * - If granted → false (nothing to ask; the existing hook already posted).
 * - If the setting is off (the user disabled it, or stored a deliberate OFF) → false, never nag.
 *
 * The actual system prompt + the post-grant re-post are wired thinly in MainActivity (the only
 * place that can host an ActivityResult launcher); this use case owns the *should we ask* rule so
 * it is unit-testable with a fake permission checker. Mirrors the reminder's enable/deny handling:
 * a denial does NOT disable the setting — it simply stays on and posts if/when the user later
 * grants notifications; we never loop or re-nag within a session (one request per launch).
 */
class ShouldRequestNotificationPermissionOnLaunchUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val permissionChecker: NotificationPermissionChecker,
    ) {
        suspend operator fun invoke(): Boolean =
            settingsRepository.persistentNotificationEnabled.first() &&
                !permissionChecker.hasNotificationPermission()
    }

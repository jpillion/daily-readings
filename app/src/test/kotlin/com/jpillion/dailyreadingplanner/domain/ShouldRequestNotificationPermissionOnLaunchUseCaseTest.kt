package com.jpillion.dailyreadingplanner.domain

import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.testing.FakeNotificationPermissionChecker
import com.jpillion.dailyreadingplanner.testing.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * S22 (amends D-S22-5): the launch-time POST_NOTIFICATIONS request rule. Now that the persistent
 * tray notification is on by default, a fresh API 33+ install must request the permission for the
 * notification to actually post — but only when the setting is on AND the permission is missing,
 * and never when it's already granted or the user turned the notification off.
 */
class ShouldRequestNotificationPermissionOnLaunchUseCaseTest {
    private val settings = FakeSettingsRepository()
    private val permission = FakeNotificationPermissionChecker()

    private val useCase = ShouldRequestNotificationPermissionOnLaunchUseCase(settings, permission)

    @Test
    fun `enabled and permission missing - requests on launch`() =
        runTest {
            // Mutation target: drop the persistentEnabled gate and a disabled install would nag.
            settings.storedPersistentEnabled.value = true
            permission.granted = false
            assertThat(useCase()).isTrue()
        }

    @Test
    fun `enabled but permission already granted - does not request`() =
        runTest {
            // Mutation target: drop the !granted gate and a granted device re-requests forever.
            settings.storedPersistentEnabled.value = true
            permission.granted = true
            assertThat(useCase()).isFalse()
        }

    @Test
    fun `disabled - never requests even when permission is missing`() =
        runTest {
            // A user who turned the notification off (or stored a deliberate OFF) is never nagged.
            settings.storedPersistentEnabled.value = false
            permission.granted = false
            assertThat(useCase()).isFalse()
        }
}

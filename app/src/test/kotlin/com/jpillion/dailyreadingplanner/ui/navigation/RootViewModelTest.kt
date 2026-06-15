package com.jpillion.dailyreadingplanner.ui.navigation

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.update.InAppUpdateManager
import com.jpillion.dailyreadingplanner.update.InAppUpdateState
import com.jpillion.dailyreadingplanner.update.UpdatePhase
import org.junit.Test

/**
 * I4 (D-I-2) — the root glue: tapping the Bible tab raises the reader's browse-reset on the shared
 * handoff. (The reset's effect on the reader is pinned in ReaderViewModelTest.)
 *
 * S-L (D-L-4) — the root also exposes the flexible-update state to the Restart snackbar and routes
 * the Restart action to the manager's completeUpdate.
 */
class RootViewModelTest {
    private class FakeUpdateManager : InAppUpdateManager {
        var completeUpdateCalls = 0
            private set

        override fun checkForUpdate(
            activity: Activity,
            launcher: ActivityResultLauncher<IntentSenderRequest>,
        ) = Unit

        override fun resume(activity: Activity) = Unit

        override fun completeUpdate() {
            completeUpdateCalls++
        }

        override fun unregister() = Unit
    }

    @Test
    fun `onBibleTabSelected raises a browse request on the handoff`() {
        val handoff = ReaderHandoff()
        val vm = RootViewModel(handoff, InAppUpdateState(), FakeUpdateManager())
        vm.onBibleTabSelected()
        assertThat(handoff.browseRequested.value).isTrue()
    }

    @Test
    fun `updatePhase reflects the shared update state`() {
        val state = InAppUpdateState()
        val vm = RootViewModel(ReaderHandoff(), state, FakeUpdateManager())
        assertThat(vm.updatePhase.value).isEqualTo(UpdatePhase.Idle)
        state.onDownloaded()
        assertThat(vm.updatePhase.value).isEqualTo(UpdatePhase.ReadyToRestart)
    }

    @Test
    fun `restartToInstallUpdate calls completeUpdate on the manager`() {
        val manager = FakeUpdateManager()
        val vm = RootViewModel(ReaderHandoff(), InAppUpdateState(), manager)
        vm.restartToInstallUpdate()
        assertThat(manager.completeUpdateCalls).isEqualTo(1)
    }
}

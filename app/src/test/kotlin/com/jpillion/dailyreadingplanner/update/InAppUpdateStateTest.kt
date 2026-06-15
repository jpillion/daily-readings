package com.jpillion.dailyreadingplanner.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * S-L (D-L-5) — the in-app-update event/state seam: the process-lifetime no-nag flag and the
 * downloaded → ReadyToRestart transition the Restart snackbar observes. Pure, JVM-testable.
 */
class InAppUpdateStateTest {
    @Test
    fun `starts idle and not-yet-prompted`() {
        val state = InAppUpdateState()
        assertThat(state.phase.value).isEqualTo(UpdatePhase.Idle)
        assertThat(state.hasPromptedThisProcess).isFalse()
    }

    @Test
    fun `markPrompted is single-shot per process (D-L-5 no-nag)`() {
        val state = InAppUpdateState()
        // First caller wins and proceeds with the check.
        assertThat(state.markPrompted()).isTrue()
        assertThat(state.hasPromptedThisProcess).isTrue()
        // Any subsequent caller this process is told not to re-check.
        assertThat(state.markPrompted()).isFalse()
        assertThat(state.markPrompted()).isFalse()
    }

    @Test
    fun `onDownloaded moves to ReadyToRestart`() {
        val state = InAppUpdateState()
        state.onDownloaded()
        assertThat(state.phase.value).isEqualTo(UpdatePhase.ReadyToRestart)
    }

    @Test
    fun `clear returns to Idle`() {
        val state = InAppUpdateState()
        state.onDownloaded()
        state.clear()
        assertThat(state.phase.value).isEqualTo(UpdatePhase.Idle)
    }
}

package com.jpillion.dailyreadingplanner.update

import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * S-L (D-L-5) — the cross-surface event/state seam for the in-app flexible update flow, modelled
 * on [com.jpillion.dailyreadingplanner.ui.navigation.ReaderHandoff]: a pure, JVM-testable holder
 * the [PlayInAppUpdateManager] writes to and the [RootScaffold] composable observes (the composable
 * can't reach the Play SDK directly, and the manager can't reach Compose state directly).
 *
 * It owns two things:
 *  1. [phase] — the only UI-relevant update state. When a flexible update finishes downloading,
 *     the manager sets [UpdatePhase.ReadyToRestart]; the root shows the Restart snackbar (D-L-4).
 *  2. [hasPromptedThisProcess] — the **process-lifetime no-nag flag** (D-L-5): the availability
 *     check fires once per process. We deliberately store NO DataStore key — re-prompting across
 *     processes is governed by Play's own update staleness, not by us persisting anything.
 *
 * `@ActivityRetainedScoped` so one instance is shared by the Activity-bound manager (producer) and
 * the root composable (consumer) for the activity's lifetime, surviving configuration change — so a
 * rotation neither re-fires the availability check nor loses a pending Restart snackbar.
 */
@ActivityRetainedScoped
class InAppUpdateState
    @Inject
    constructor() {
        private val _phase = MutableStateFlow<UpdatePhase>(UpdatePhase.Idle)

        /** The current update phase. [UpdatePhase.ReadyToRestart] ⇒ the root shows the Restart snackbar. */
        val phase: StateFlow<UpdatePhase> = _phase.asStateFlow()

        /** True once this process has run the availability check (D-L-5 no-nag). Set by [markPrompted]. */
        var hasPromptedThisProcess: Boolean = false
            private set

        /**
         * Records that the one-per-process availability check has run, and returns whether the
         * caller is the one that flipped it (i.e. the check should proceed). A second call in the
         * same process returns false, so the manager runs the Play check at most once per process.
         */
        fun markPrompted(): Boolean {
            if (hasPromptedThisProcess) return false
            hasPromptedThisProcess = true
            return true
        }

        /** The flexible update finished downloading: the root should offer Restart (D-L-4). */
        fun onDownloaded() {
            _phase.value = UpdatePhase.ReadyToRestart
        }

        /** Clear the ready-to-restart state (e.g. after the user dismisses the snackbar without restarting). */
        fun clear() {
            _phase.value = UpdatePhase.Idle
        }
    }

/** The UI-relevant phases of the flexible update flow. */
sealed interface UpdatePhase {
    /** No update, or one still downloading in the background — nothing for the UI to show. */
    data object Idle : UpdatePhase

    /** A flexible update has downloaded and is staged; the UI offers a Restart action (D-L-4). */
    data object ReadyToRestart : UpdatePhase
}

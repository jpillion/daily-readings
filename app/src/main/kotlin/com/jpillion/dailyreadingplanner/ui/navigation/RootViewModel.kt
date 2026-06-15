package com.jpillion.dailyreadingplanner.ui.navigation

import androidx.lifecycle.ViewModel
import com.jpillion.dailyreadingplanner.update.InAppUpdateManager
import com.jpillion.dailyreadingplanner.update.InAppUpdateState
import com.jpillion.dailyreadingplanner.update.UpdatePhase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * I4 (D-I-2, OQ-A) — root-level glue so the Bible **tab** click can raise the reader's
 * "reset to Browse" signal on the shared [ReaderHandoff] (which is `@ActivityRetainedScoped`, hence
 * not directly accessible from the [RootScaffold] composable). A reading-tap handoff goes the other
 * way (`ReaderHandoff.request`) and is unaffected — the two are mutually exclusive in the seam.
 *
 * S-L (D-L-4) — it ALSO exposes the in-app flexible-update state to the root: [updatePhase] drives
 * the Restart snackbar, and [restartToInstallUpdate] is the snackbar's Restart action (installs the
 * staged update and restarts). The [InAppUpdateState] and [InAppUpdateManager] are
 * `@ActivityRetainedScoped`, the same instances the Activity drives, so the snackbar reflects the
 * real download state and survives rotation.
 */
@HiltViewModel
class RootViewModel
    @Inject
    constructor(
        private val readerHandoff: ReaderHandoff,
        private val inAppUpdateState: InAppUpdateState,
        private val inAppUpdateManager: InAppUpdateManager,
    ) : ViewModel() {
        /** The user tapped the Bible tab in the nav bar: reset the reader to single-chapter Browse. */
        fun onBibleTabSelected() {
            readerHandoff.requestBrowse()
        }

        /** [UpdatePhase.ReadyToRestart] ⇒ the root shows the Restart snackbar (D-L-4). */
        val updatePhase: StateFlow<UpdatePhase> = inAppUpdateState.phase

        /** The Restart snackbar action: install the staged flexible update and restart. */
        fun restartToInstallUpdate() {
            inAppUpdateManager.completeUpdate()
        }
    }

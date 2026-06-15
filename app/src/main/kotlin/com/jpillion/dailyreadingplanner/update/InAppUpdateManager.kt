package com.jpillion.dailyreadingplanner.update

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest

/**
 * S-L (D-L-1) — the seam the Activity drives for the Play In-App Updates flexible flow. Keeping the
 * Activity ↔ Play wiring behind this interface lets MainActivity stay free of Play SDK types and
 * lets the wiring be swapped/faked. The real implementation is [PlayInAppUpdateManager].
 *
 * Lifecycle contract (MainActivity, L4):
 *  - [checkForUpdate] once after the launch hooks: queries Play, applies [UpdatePromptDecision],
 *    and (if warranted) starts the flexible flow via [launcher]. No-ops after the first call this
 *    process (D-L-5).
 *  - [resume] on every onResume: if a flexible update already finished downloading (e.g. while the
 *    app was backgrounded), re-surfaces the Restart state so the snackbar reappears.
 *  - [completeUpdate] from the Restart snackbar action: installs the staged update and restarts.
 *  - [unregister] in onDestroy: detaches the Play install listener.
 */
interface InAppUpdateManager {
    /**
     * Run the one-per-process availability check; if a minor-or-higher update is offered, start the
     * flexible flow. [launcher] receives the Play update consent intent.
     */
    fun checkForUpdate(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
    )

    /** Re-check for an already-downloaded flexible update (stalled while backgrounded). */
    fun resume(activity: Activity)

    /** Install the staged flexible update and restart (the Restart snackbar action). */
    fun completeUpdate()

    /** Detach the Play install-state listener (call from onDestroy). */
    fun unregister()
}

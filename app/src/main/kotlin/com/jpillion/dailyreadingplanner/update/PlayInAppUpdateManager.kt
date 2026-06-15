package com.jpillion.dailyreadingplanner.update

import android.app.Activity
import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import com.google.android.play.core.ktx.requestCompleteUpdate
import com.jpillion.dailyreadingplanner.BuildConfig
import com.jpillion.dailyreadingplanner.di.DefaultDispatcher
import com.jpillion.dailyreadingplanner.domain.UpdateAvailabilitySignal
import com.jpillion.dailyreadingplanner.domain.UpdatePromptDecision
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * S-L (D-L-1) — the real Play In-App Updates wiring behind [InAppUpdateManager], using the FLEXIBLE
 * (non-blocking, background-download) flow only. Never an immediate/blocking forced update
 * (FR-NV9 / new-version-notice §6 Option A).
 *
 * Flow:
 *  1. [checkForUpdate] (once per process, D-L-5): query Play for [AppUpdateInfo], map it onto the
 *     pure [UpdateAvailabilitySignal], and ask [UpdatePromptDecision.shouldPrompt] — so the
 *     minor-vs-patch gate (a PATCH bump is silent) is decided by the JVM-pinned pure rule, not by
 *     Play. If it warrants a prompt, start the flexible flow via [startUpdateFlowForResult].
 *  2. A registered [InstallStateUpdatedListener] watches the background download; on
 *     [InstallStatus.DOWNLOADED] it pushes [InAppUpdateState.onDownloaded] so the root shows the
 *     Restart snackbar (D-L-4). The download itself never interrupts the reader.
 *  3. [resume] re-checks for an already-DOWNLOADED update (the user backgrounded the app mid/post
 *     download) and re-surfaces the Restart state.
 *  4. [completeUpdate] (the snackbar's Restart action) installs the staged update and restarts.
 *
 * `@ActivityRetainedScoped` so it shares the [InAppUpdateState] instance and survives rotation
 * without re-firing the check. All Play calls are off the main thread (the suspend ktx APIs run on
 * the injected dispatcher) so StrictMode stays clean. Every Play call is wrapped: a Play-less device
 * or a failed check is simply inert (FR-NV6/NV10) — no crash, no error surfaced to the reader.
 */
@ActivityRetainedScoped
class PlayInAppUpdateManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val state: InAppUpdateState,
        @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
    ) : InAppUpdateManager {
        private val scope = CoroutineScope(SupervisorJob() + dispatcher)

        private val appUpdateManager: AppUpdateManager by lazy {
            AppUpdateManagerFactory.create(context)
        }

        private val installListener =
            InstallStateUpdatedListener { installState: InstallState ->
                if (installState.installStatus() == InstallStatus.DOWNLOADED) {
                    state.onDownloaded()
                }
            }

        private var listenerRegistered = false

        override fun checkForUpdate(
            activity: Activity,
            launcher: ActivityResultLauncher<IntentSenderRequest>,
        ) {
            // D-L-5: at most one availability check per process.
            if (!state.markPrompted()) return
            registerListenerOnce()
            scope.launch {
                val info = runCatching { appUpdateManager.requestAppUpdateInfo() }.getOrNull() ?: return@launch
                if (UpdatePromptDecision.shouldPrompt(
                        currentVersionCode = BuildConfig.VERSION_CODE,
                        availableVersionCode = info.availableVersionCode(),
                        signal = info.toSignal(),
                    )
                ) {
                    runCatching {
                        appUpdateManager.startUpdateFlowForResult(
                            info,
                            launcher,
                            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                        )
                    }
                } else if (info.installStatus() == InstallStatus.DOWNLOADED) {
                    // An update finished downloading in a prior session but was never installed.
                    state.onDownloaded()
                }
            }
        }

        override fun resume(activity: Activity) {
            scope.launch {
                val info = runCatching { appUpdateManager.requestAppUpdateInfo() }.getOrNull() ?: return@launch
                if (info.installStatus() == InstallStatus.DOWNLOADED) {
                    state.onDownloaded()
                }
            }
        }

        override fun completeUpdate() {
            scope.launch {
                runCatching { appUpdateManager.requestCompleteUpdate() }
            }
        }

        override fun unregister() {
            if (listenerRegistered) {
                appUpdateManager.unregisterListener(installListener)
                listenerRegistered = false
            }
        }

        private fun registerListenerOnce() {
            if (!listenerRegistered) {
                appUpdateManager.registerListener(installListener)
                listenerRegistered = true
            }
        }
    }

/** Maps Play's availability surface onto the pure [UpdateAvailabilitySignal] (UI-free). */
private fun AppUpdateInfo.toSignal(): UpdateAvailabilitySignal =
    if (updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
        isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
    ) {
        UpdateAvailabilitySignal.UPDATE_AVAILABLE
    } else {
        UpdateAvailabilitySignal.NONE
    }

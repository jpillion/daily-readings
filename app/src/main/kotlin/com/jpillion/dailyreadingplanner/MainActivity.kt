package com.jpillion.dailyreadingplanner

import android.Manifest
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.jpillion.dailyreadingplanner.domain.RescheduleAlarmsUseCase
import com.jpillion.dailyreadingplanner.domain.ShouldRequestNotificationPermissionOnLaunchUseCase
import com.jpillion.dailyreadingplanner.ui.navigation.RootScaffold
import com.jpillion.dailyreadingplanner.ui.theme.DailyReadingPlannerTheme
import com.jpillion.dailyreadingplanner.ui.theme.ThemeViewModel
import com.jpillion.dailyreadingplanner.ui.theme.resolveDarkTheme
import com.jpillion.dailyreadingplanner.update.InAppUpdateManager
import com.jpillion.dailyreadingplanner.widget.WidgetRefresher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val themeViewModel: ThemeViewModel by viewModels()

    @Inject
    lateinit var widgetRefresher: WidgetRefresher

    @Inject
    lateinit var rescheduleAlarms: RescheduleAlarmsUseCase

    @Inject
    lateinit var shouldRequestNotificationPermissionOnLaunch:
        ShouldRequestNotificationPermissionOnLaunchUseCase

    @Inject
    lateinit var inAppUpdateManager: InAppUpdateManager

    // S-L (D-L-1): receives the Play flexible-update consent intent. The result is irrelevant to
    // us — the download proceeds in the background and the InstallStateUpdatedListener (in the
    // manager) is what raises the Restart snackbar on DOWNLOADED — so the callback is a no-op.
    private val updateFlowLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

    // S22 (amends D-S22-5): the persistent tray notification is now ON by default, so a fresh
    // API 33+ install must request POST_NOTIFICATIONS for it to post. On grant we re-run the
    // reschedule/refresh hook so the notification appears immediately; on denial the setting
    // stays on but simply can't post (no crash, no nag) — mirrors the reminder's denial handling.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                lifecycleScope.launch { rescheduleAlarms() }
            }
        }

    override fun onResume() {
        super.onResume()
        // Opportunistic widget refresh on resume (D9/ESpec §7): the dominant date-rollover
        // case — opening the app after midnight snaps the widget to the new day.
        lifecycleScope.launch { widgetRefresher.refreshTodayWidget() }
        // S-L: if a flexible update finished downloading while we were backgrounded, re-surface
        // the Restart snackbar (Play's recommended onResume re-check). Inert if none/Play-less.
        inAppUpdateManager.resume(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // S19 (D-S19-2): the first-run tracking-start prompt is resolved and rendered by the
        // day screen's ViewModel/UI — no MainActivity hook (one less JVM-untested launch here).
        // S12 (R-REM-8 + D-S12-2): re-arm the standing alarms from persisted settings on
        // every launch — idempotent, covers app update and relaunch after a force-stop.
        // S22 (amends D-S22-5): once the alarms are armed, request POST_NOTIFICATIONS if the
        // (now default-on) persistent notification is enabled but the permission isn't granted.
        // Fired once per launch, after the reschedule, so it isn't stacked behind the day
        // screen's in-app first-run dialogs — the OS prompt is its own surface. A denial is
        // non-fatal: the setting stays on and posts if/when notifications are later allowed.
        lifecycleScope.launch {
            rescheduleAlarms()
            if (shouldRequestNotificationPermissionOnLaunch()) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        enableEdgeToEdge()
        setContent {
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val fontScale by themeViewModel.fontScale.collectAsStateWithLifecycle()
            val darkTheme = themeMode.resolveDarkTheme()

            // Keep system-bar icon contrast in sync with the *app* theme, not the device
            // theme (FR-9): re-issue enableEdgeToEdge whenever the resolved theme changes.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle =
                        SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle =
                        SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                )
                onDispose {}
            }

            DailyReadingPlannerTheme(darkTheme = darkTheme, fontScale = fontScale) {
                RootScaffold()
            }
        }
        // S-L (D-L-1/D-L-5): the one-per-process Play update availability check. The
        // minor-vs-patch gate is decided by UpdatePromptDecision inside the manager; a PATCH
        // bump is silent, a minor-or-higher one starts the non-blocking flexible flow. Inert on
        // Play-less devices or when the check fails (FR-NV6/NV10) — never blocks the readings.
        inAppUpdateManager.checkForUpdate(this, updateFlowLauncher)
    }

    override fun onDestroy() {
        // S-L: detach the Play install-state listener so it doesn't outlive the activity.
        inAppUpdateManager.unregister()
        super.onDestroy()
    }
}

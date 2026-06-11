package com.jpillion.dailyreadingplanner

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.jpillion.dailyreadingplanner.domain.InitializeTrackingStartUseCase
import com.jpillion.dailyreadingplanner.ui.navigation.AppNavHost
import com.jpillion.dailyreadingplanner.ui.theme.DailyReadingPlannerTheme
import com.jpillion.dailyreadingplanner.ui.theme.ThemeViewModel
import com.jpillion.dailyreadingplanner.ui.theme.resolveDarkTheme
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
    lateinit var initializeTrackingStart: InitializeTrackingStartUseCase

    override fun onResume() {
        super.onResume()
        // Opportunistic widget refresh on resume (D9/ESpec §7): the dominant date-rollover
        // case — opening the app after midnight snaps the widget to the new day.
        lifecycleScope.launch { widgetRefresher.refreshTodayWidget() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // One-time tracking-start default (S10, D-S10-1); idempotent via its marker pref.
        lifecycleScope.launch { initializeTrackingStart() }
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
                AppNavHost()
            }
        }
    }
}

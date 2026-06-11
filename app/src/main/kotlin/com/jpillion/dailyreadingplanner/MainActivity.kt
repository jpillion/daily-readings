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
import com.jpillion.dailyreadingplanner.ui.navigation.AppNavHost
import com.jpillion.dailyreadingplanner.ui.theme.DailyReadingPlannerTheme
import com.jpillion.dailyreadingplanner.ui.theme.ThemeViewModel
import com.jpillion.dailyreadingplanner.ui.theme.resolveDarkTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val themeViewModel: ThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
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

            DailyReadingPlannerTheme(darkTheme = darkTheme) {
                AppNavHost()
            }
        }
    }
}

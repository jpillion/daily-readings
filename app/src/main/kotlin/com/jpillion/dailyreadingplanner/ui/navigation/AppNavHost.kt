package com.jpillion.dailyreadingplanner.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jpillion.dailyreadingplanner.ui.day.DayReadingsRoute
import com.jpillion.dailyreadingplanner.ui.settings.SettingsRoute

object Routes {
    const val TODAY = "today"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.TODAY) {
        // The day pager replaces Sprint 4's single-day Today screen (D-S5-1); the date picker
        // is a dialog over it, not a pushed route (D-S5-2). Settings is the first pushed
        // route (ESpec §7); Today remains the back-stack root.
        // S15 (D-S15-4): the stats route is gone — stats render inline on the day screen.
        composable(Routes.TODAY) {
            DayReadingsRoute(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsRoute(onBack = { navController.popBackStack() })
        }
    }
}

package com.jpillion.dailyreadingplanner.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jpillion.dailyreadingplanner.ui.day.DayReadingsRoute

object Routes {
    const val TODAY = "today"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.TODAY) {
        // The day pager replaces Sprint 4's single-day Today screen (D-S5-1); the date picker
        // is a dialog over it, not a pushed route (D-S5-2), so Today stays the only route.
        composable(Routes.TODAY) { DayReadingsRoute() }
    }
}

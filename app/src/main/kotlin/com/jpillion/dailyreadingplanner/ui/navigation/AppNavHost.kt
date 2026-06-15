package com.jpillion.dailyreadingplanner.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jpillion.dailyreadingplanner.bible.ui.reader.ReaderRoute
import com.jpillion.dailyreadingplanner.ui.day.DayReadingsRoute
import com.jpillion.dailyreadingplanner.ui.settings.SettingsRoute

object Routes {
    const val TODAY = "today"
    const val SETTINGS = "settings"

    // SPRINT C TEMPORARY — a plain pushed route so the in-app reader is reachable/testable now.
    // Sprint D (D-V3-16) replaces this with the co-equal Schedule | Bible bottom-nav graph; the
    // reading-tap → in-app handoff lands there too.
    const val READER = "reader"
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
                // SPRINT C TEMPORARY — see Routes.READER. Replaced by the Sprint D bottom-nav.
                onOpenReader = { navController.navigate(Routes.READER) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsRoute(onBack = { navController.popBackStack() })
        }
        // SPRINT C TEMPORARY route (Routes.READER). The reader is otherwise stateless of nav.
        composable(Routes.READER) {
            ReaderRoute()
        }
    }
}

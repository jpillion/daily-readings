package com.jpillion.dailyreadingplanner.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jpillion.dailyreadingplanner.ui.today.TodayRoute

object Routes {
    const val TODAY = "today"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.TODAY) {
        composable(Routes.TODAY) { TodayRoute() }
    }
}

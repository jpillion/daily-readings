package com.jpillion.dailyreadingplanner.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.jpillion.dailyreadingplanner.R
import com.jpillion.dailyreadingplanner.bible.ui.reader.ReaderRoute
import com.jpillion.dailyreadingplanner.ui.day.DayReadingsRoute
import com.jpillion.dailyreadingplanner.ui.settings.SettingsRoute

/** Nested-graph roots (D-V3-16): two co-equal tabs, Schedule the start destination. */
object Graph {
    const val SCHEDULE = "graph_schedule"
    const val BIBLE = "graph_bible"
}

object Routes {
    const val TODAY = "today"
    const val SETTINGS = "settings"
    const val READER = "reader"
}

/**
 * VD-T1 (D-V3-16) — the app root: a co-equal Schedule | Bible [NavigationBar] over two nested
 * graphs, with Schedule as the start destination. This reframes the app as planner + reader and
 * permanently spends ~80dp on the bar (R-V3-1, owner-accepted). Replaces the Sprint-4..C single
 * NavHost (and retires the Sprint-C temporary reader push).
 */
@Composable
fun RootScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentDestination.isInGraph(Graph.SCHEDULE),
                    onClick = { navController.switchTab(Graph.SCHEDULE) },
                    icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_schedule)) },
                    modifier = Modifier.testTag("nav-schedule"),
                )
                NavigationBarItem(
                    selected = currentDestination.isInGraph(Graph.BIBLE),
                    onClick = { navController.switchTab(Graph.BIBLE) },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_bible)) },
                    modifier = Modifier.testTag("nav-bible"),
                )
            }
        },
    ) { padding ->
        AppNavHost(navController, Modifier.padding(padding))
    }
}

/**
 * VD-T2 (D-D-3) — two nested graphs, `startDestination = Graph.SCHEDULE`. The Schedule graph
 * keeps the existing day-pager (start) + pushed Settings; the Bible graph hosts the reader. The
 * reading-tap in-app handoff (VD-T5) switches to the Bible tab and the reader consumes the
 * pending portion from [ReaderHandoff] — no route argument crosses the graph boundary.
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Graph.SCHEDULE,
        modifier = modifier,
    ) {
        navigation(startDestination = Routes.TODAY, route = Graph.SCHEDULE) {
            composable(Routes.TODAY) {
                DayReadingsRoute(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenInApp = { navController.switchTab(Graph.BIBLE) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsRoute(onBack = { navController.popBackStack() })
            }
        }
        navigation(startDestination = Routes.READER, route = Graph.BIBLE) {
            composable(Routes.READER) {
                ReaderRoute()
            }
        }
    }
}

/** True when [this] destination belongs to the graph rooted at [graphRoute]. */
internal fun NavDestination?.isInGraph(graphRoute: String): Boolean =
    this?.hierarchy?.any { it.route == graphRoute } == true

/**
 * Co-equal tab switch with per-tab state preservation (U18, D-D-3): pop up to the graph root
 * saving its state, single-top, and restore the target graph's saved state — so the reader keeps
 * its chapter and the schedule keeps its day across a tab switch within a session.
 */
internal fun NavController.switchTab(graphRoute: String) {
    navigate(graphRoute) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

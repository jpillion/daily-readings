package com.jpillion.dailyreadingplanner.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.testing.TestNavHostController
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * VD-T3 (D-V3-17, R-V3-5) — nav-regression coverage for the Sprint D restructure. Asserts the
 * production graph topology + tab semantics: Schedule is the start destination, both tabs are
 * reachable, every existing screen (day-pager, Settings, reader) stays reachable, and back-stack
 * state is preserved across a tab switch. This retires part of the long-standing Sprint-6
 * AppNavHost JVM-untested debt.
 *
 * The graph here mirrors production `Graph`/`Routes` and drives the production [switchTab] /
 * [isInGraph] helpers (the load-bearing wiring); only the leaf screen bodies are tagged stand-ins
 * so the test needs no Hilt VM graph.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavRegressionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: TestNavHostController

    /** Mirrors the production nested-graph structure with tagged stand-in screens. */
    @Composable
    private fun TestGraph() {
        navController =
            TestNavHostController(androidx.compose.ui.platform.LocalContext.current).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
        NavHost(navController = navController, startDestination = Graph.SCHEDULE) {
            navigation(startDestination = Routes.TODAY, route = Graph.SCHEDULE) {
                composable(Routes.TODAY) { Text("today", modifier = Modifier.testTag("screen-today")) }
                composable(Routes.SETTINGS) { Text("settings", modifier = Modifier.testTag("screen-settings")) }
            }
            navigation(startDestination = Routes.READER, route = Graph.BIBLE) {
                composable(Routes.READER) { Text("reader", modifier = Modifier.testTag("screen-reader")) }
            }
        }
    }

    private fun currentRoute(): String? = navController.currentDestination?.route

    private fun inGraph(graph: String): Boolean = navController.currentDestination?.isInGraph(graph) == true

    @Test
    fun `schedule is the start destination - the day pager shows first`() {
        composeRule.setContent { TestGraph() }
        assertThat(currentRoute()).isEqualTo(Routes.TODAY)
        assertThat(inGraph(Graph.SCHEDULE)).isTrue()
        assertThat(inGraph(Graph.BIBLE)).isFalse()
    }

    @Test
    fun `settings stays reachable within the schedule graph`() {
        composeRule.setContent { TestGraph() }
        composeRule.runOnUiThread { navController.navigate(Routes.SETTINGS) }
        assertThat(currentRoute()).isEqualTo(Routes.SETTINGS)
        assertThat(inGraph(Graph.SCHEDULE)).isTrue()
    }

    @Test
    fun `the bible tab is reachable and lands on the reader`() {
        composeRule.setContent { TestGraph() }
        composeRule.runOnUiThread { navController.switchTab(Graph.BIBLE) }
        assertThat(currentRoute()).isEqualTo(Routes.READER)
        assertThat(inGraph(Graph.BIBLE)).isTrue()
        assertThat(inGraph(Graph.SCHEDULE)).isFalse()
    }

    @Test
    fun `a tab switch preserves the schedule back-stack state`() {
        composeRule.setContent { TestGraph() }
        // Drill into Settings on the Schedule tab.
        composeRule.runOnUiThread { navController.navigate(Routes.SETTINGS) }
        assertThat(currentRoute()).isEqualTo(Routes.SETTINGS)
        // Switch to Bible, then back to Schedule.
        composeRule.runOnUiThread { navController.switchTab(Graph.BIBLE) }
        assertThat(currentRoute()).isEqualTo(Routes.READER)
        composeRule.runOnUiThread { navController.switchTab(Graph.SCHEDULE) }
        // Restored to where we left the Schedule tab — Settings, not the day pager.
        assertThat(currentRoute()).isEqualTo(Routes.SETTINGS)
    }

    @Test
    fun `isInGraph distinguishes the two tabs across the hierarchy`() {
        composeRule.setContent { TestGraph() }
        composeRule.runOnUiThread { navController.switchTab(Graph.BIBLE) }
        val dest = navController.currentDestination
        assertThat(dest?.hierarchy?.any { it.route == Graph.BIBLE }).isTrue()
        assertThat(dest.isInGraph(Graph.BIBLE)).isTrue()
        assertThat(dest.isInGraph(Graph.SCHEDULE)).isFalse()
    }
}

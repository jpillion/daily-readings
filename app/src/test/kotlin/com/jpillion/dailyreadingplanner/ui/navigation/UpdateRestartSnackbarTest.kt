package com.jpillion.dailyreadingplanner.ui.navigation

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.ui.theme.DailyReadingPlannerTheme
import com.jpillion.dailyreadingplanner.update.UpdatePhase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S-L (D-L-4) — the Restart snackbar surface: it appears only when a flexible update has finished
 * downloading (phase == ReadyToRestart), carries the sober message + a spoken "Restart" action, and
 * the action triggers the install/restart. The 48dp touch target for the action is pinned in
 * AccessibilityGateTest; here we pin the wording, conditional appearance, and the action callback.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateRestartSnackbarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `no snackbar while idle`() {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                val host = remember { SnackbarHostState() }
                UpdateRestartSnackbarEffect(UpdatePhase.Idle, host, onRestart = {})
                SnackbarHost(host)
            }
        }
        composeRule.onNodeWithText("An update is ready to install.").assertDoesNotExist()
        composeRule.onNodeWithText("Restart").assertDoesNotExist()
    }

    @Test
    fun `ready-to-restart shows the sober message and a Restart action`() {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                val host = remember { SnackbarHostState() }
                UpdateRestartSnackbarEffect(UpdatePhase.ReadyToRestart, host, onRestart = {})
                SnackbarHost(host)
            }
        }
        composeRule.onNodeWithText("An update is ready to install.").assertIsDisplayed()
        composeRule.onNodeWithText("Restart").assertIsDisplayed()
    }

    @Test
    fun `tapping Restart invokes the restart callback`() {
        var restarts = 0
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                val host = remember { SnackbarHostState() }
                UpdateRestartSnackbarEffect(UpdatePhase.ReadyToRestart, host, onRestart = { restarts++ })
                SnackbarHost(host)
            }
        }
        composeRule.onNodeWithText("Restart").performClick()
        composeRule.runOnIdle { assertThat(restarts).isEqualTo(1) }
    }
}

package com.jpillion.dailyreadingplanner.ui.theme

import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.domain.model.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [resolveDarkTheme] mapping (FR-9): LIGHT/DARK are absolute regardless of the device
 * setting; SYSTEM follows the device night qualifier.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResolveDarkThemeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun resolveAll(): Map<ThemeMode, Boolean> {
        val resolved = mutableMapOf<ThemeMode, Boolean>()
        composeRule.setContent {
            ThemeMode.entries.forEach { mode -> resolved[mode] = mode.resolveDarkTheme() }
        }
        return resolved
    }

    @Test
    @Config(sdk = [34], qualifiers = "notnight")
    fun deviceInLightMode_lightAndDarkAreAbsolute_systemFollowsDevice() {
        val resolved = resolveAll()
        assertThat(resolved[ThemeMode.LIGHT]).isFalse()
        assertThat(resolved[ThemeMode.DARK]).isTrue()
        assertThat(resolved[ThemeMode.SYSTEM]).isFalse()
    }

    @Test
    @Config(sdk = [34], qualifiers = "night")
    fun deviceInDarkMode_lightAndDarkAreAbsolute_systemFollowsDevice() {
        val resolved = resolveAll()
        assertThat(resolved[ThemeMode.LIGHT]).isFalse()
        assertThat(resolved[ThemeMode.DARK]).isTrue()
        assertThat(resolved[ThemeMode.SYSTEM]).isTrue()
    }
}

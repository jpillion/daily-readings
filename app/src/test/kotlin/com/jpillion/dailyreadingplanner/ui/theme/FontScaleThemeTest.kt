package com.jpillion.dailyreadingplanner.ui.theme

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S8 (D-S8-5): the theme's [fontScale] multiplies the ambient density font scale — so the
 * Settings text-size choice composes with (never replaces) the system a11y font setting.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FontScaleThemeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `theme multiplies the ambient font scale by the settings factor`() {
        var ambient = 0f
        var inside = 0f
        composeRule.setContent {
            ambient = LocalDensity.current.fontScale
            DailyReadingPlannerTheme(dynamicColor = false, fontScale = 1.5f) {
                inside = LocalDensity.current.fontScale
            }
        }
        assertThat(inside).isWithin(1e-4f).of(ambient * 1.5f)
    }

    @Test
    fun `default factor leaves the system font scale untouched`() {
        var ambient = 0f
        var inside = 0f
        composeRule.setContent {
            ambient = LocalDensity.current.fontScale
            DailyReadingPlannerTheme(dynamicColor = false) {
                inside = LocalDensity.current.fontScale
            }
        }
        assertThat(inside).isWithin(1e-4f).of(ambient)
    }

    @Test
    fun `density - dp sizing - is not affected by the text-size factor`() {
        var ambientDensity = 0f
        var insideDensity = 0f
        composeRule.setContent {
            ambientDensity = LocalDensity.current.density
            DailyReadingPlannerTheme(dynamicColor = false, fontScale = 1.5f) {
                insideDensity = LocalDensity.current.density
            }
        }
        assertThat(insideDensity).isWithin(1e-4f).of(ambientDensity)
    }
}

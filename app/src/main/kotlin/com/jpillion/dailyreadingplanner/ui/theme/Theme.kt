package com.jpillion.dailyreadingplanner.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.jpillion.dailyreadingplanner.domain.model.ThemeMode

// Internal (not private): the Glance widget reuses the static schemes for its own
// light/dark ColorProviders below API 31 (D-S7-3).
internal val LightColorScheme =
    lightColorScheme(
        primary = GreenPrimary,
        onPrimary = GreenOnPrimary,
        primaryContainer = GreenPrimaryContainer,
        onPrimaryContainer = GreenOnPrimaryContainer,
        secondary = GreenSecondary,
        onSecondary = GreenOnSecondary,
        secondaryContainer = GreenSecondaryContainer,
        onSecondaryContainer = GreenOnSecondaryContainer,
        tertiary = GreenTertiary,
        onTertiary = GreenOnTertiary,
    )

internal val DarkColorScheme =
    darkColorScheme(
        primary = GreenPrimaryDark,
        onPrimary = GreenOnPrimaryDark,
        primaryContainer = GreenPrimaryContainerDark,
        onPrimaryContainer = GreenOnPrimaryContainerDark,
        secondary = GreenSecondaryDark,
        onSecondary = GreenOnSecondaryDark,
        secondaryContainer = GreenSecondaryContainerDark,
        onSecondaryContainer = GreenOnSecondaryContainerDark,
        tertiary = GreenTertiaryDark,
        onTertiary = GreenOnTertiaryDark,
    )

/**
 * Maps the persisted [ThemeMode] to the boolean [DailyReadingPlannerTheme] consumes (FR-9):
 * LIGHT and DARK are absolute; SYSTEM follows the device dark-mode setting.
 */
@Composable
fun ThemeMode.resolveDarkTheme(): Boolean =
    when (this) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

/**
 * App theme (S2-T4). Follows the system light/dark setting by default; Sprint 6's Settings
 * screen drives [darkTheme] from the persisted ThemeMode via [resolveDarkTheme]. Dynamic
 * color (D8) is honored on API 31+ with the static green palette as fallback.
 *
 * [fontScale] (S8, D-S8-5) multiplies the density's font scale, so every sp-sized text in
 * the app — and nothing dp-sized — scales by the user's Settings choice *on top of* the
 * system font-size setting (a11y composes, never overrides).
 */
@Composable
fun DailyReadingPlannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
    ) {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, density.fontScale * fontScale),
            content = content,
        )
    }
}

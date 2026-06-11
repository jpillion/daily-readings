package com.jpillion.dailyreadingplanner.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme =
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

private val DarkColorScheme =
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
 * App theme (S2-T4). Follows the system light/dark setting by default; Sprint 6's Settings
 * screen will drive [darkTheme] from the persisted ThemeMode. Dynamic color (D8) is honored
 * on API 31+ with the static green palette as fallback.
 */
@Composable
fun DailyReadingPlannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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
        content = content,
    )
}

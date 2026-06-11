package com.jpillion.dailyreadingplanner.ui.theme

import androidx.compose.ui.graphics.Color

// Static fallback palette (used below API 31, where dynamic color is unavailable).
// Seed: a calm scripture-green.
val GreenPrimary = Color(0xFF2E6B4F)
val GreenOnPrimary = Color(0xFFFFFFFF)
val GreenPrimaryContainer = Color(0xFFB1F1CE)
val GreenOnPrimaryContainer = Color(0xFF002112)
val GreenSecondary = Color(0xFF4D6357)
val GreenOnSecondary = Color(0xFFFFFFFF)
val GreenSecondaryContainer = Color(0xFFCFE9D8)
val GreenOnSecondaryContainer = Color(0xFF0A1F16)
val GreenTertiary = Color(0xFF3D6473)
val GreenOnTertiary = Color(0xFFFFFFFF)

val GreenPrimaryDark = Color(0xFF96D5B3)
val GreenOnPrimaryDark = Color(0xFF003824)
val GreenPrimaryContainerDark = Color(0xFF115236)
val GreenOnPrimaryContainerDark = Color(0xFFB1F1CE)
val GreenSecondaryDark = Color(0xFFB4CCBC)
val GreenOnSecondaryDark = Color(0xFF20352A)
val GreenSecondaryContainerDark = Color(0xFF364B40)
val GreenOnSecondaryContainerDark = Color(0xFFCFE9D8)
val GreenTertiaryDark = Color(0xFFA5CDDE)
val GreenOnTertiaryDark = Color(0xFF073543)

// Date-picker completion indicators (S8, D-S8-2): fixed semantic green/red — independent of
// dynamic color so "done" and "missed" read consistently — with a lighter pair for dark
// surfaces. The dot is never the only signal (cell contentDescription speaks the state).
val IndicatorGreenLight = Color(0xFF2E7D32)
val IndicatorGreenDark = Color(0xFF81C784)
val IndicatorRedLight = Color(0xFFC62828)
val IndicatorRedDark = Color(0xFFEF9A9A)

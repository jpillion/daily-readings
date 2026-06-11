package com.jpillion.dailyreadingplanner.data.prefs

import com.jpillion.dailyreadingplanner.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * Appearance preference persistence (ESpec §5.5 + S8): the theme mode and the text-size
 * scale. Defaults: [ThemeMode.SYSTEM] and [DEFAULT_FONT_SCALE].
 */
interface ThemeRepository {
    val themeMode: Flow<ThemeMode>

    /**
     * App-wide text scale factor (S8, D-S8-5), multiplied on top of the system font scale.
     * Always within [MIN_FONT_SCALE]..[MAX_FONT_SCALE]. Does not affect the widget (D-S7-3:
     * launcher surfaces follow system settings).
     */
    val fontScale: Flow<Float>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setFontScale(scale: Float)

    companion object {
        const val MIN_FONT_SCALE = 0.85f
        const val MAX_FONT_SCALE = 1.5f
        const val DEFAULT_FONT_SCALE = 1.0f
    }
}

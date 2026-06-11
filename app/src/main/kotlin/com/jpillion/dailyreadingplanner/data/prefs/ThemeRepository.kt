package com.jpillion.dailyreadingplanner.data.prefs

import com.jpillion.dailyreadingplanner.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/** Theme preference persistence (ESpec §5.5). Default is [ThemeMode.SYSTEM]. */
interface ThemeRepository {
    val themeMode: Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)
}

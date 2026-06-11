package com.jpillion.dailyreadingplanner.testing

import com.jpillion.dailyreadingplanner.data.prefs.ThemeRepository
import com.jpillion.dailyreadingplanner.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [ThemeRepository] for ViewModel tests; records writes. */
class FakeThemeRepository(
    initial: ThemeMode = ThemeMode.SYSTEM,
) : ThemeRepository {
    val stored = MutableStateFlow(initial)
    val setCalls = mutableListOf<ThemeMode>()
    val storedFontScale = MutableStateFlow(ThemeRepository.DEFAULT_FONT_SCALE)
    val fontScaleCalls = mutableListOf<Float>()

    override val themeMode: Flow<ThemeMode> = stored

    override val fontScale: Flow<Float> = storedFontScale

    override suspend fun setThemeMode(mode: ThemeMode) {
        setCalls += mode
        stored.value = mode
    }

    override suspend fun setFontScale(scale: Float) {
        fontScaleCalls += scale
        storedFontScale.value = scale
    }
}

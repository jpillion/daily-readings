package com.jpillion.dailyreadingplanner.testing

import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

/** In-memory [SettingsRepository] for ViewModel tests; records writes. */
class FakeSettingsRepository(
    initial: ThemeMode = ThemeMode.SYSTEM,
) : SettingsRepository {
    val stored = MutableStateFlow(initial)
    val setCalls = mutableListOf<ThemeMode>()
    val storedFontScale = MutableStateFlow(SettingsRepository.DEFAULT_FONT_SCALE)
    val fontScaleCalls = mutableListOf<Float>()

    override val themeMode: Flow<ThemeMode> = stored

    override val fontScale: Flow<Float> = storedFontScale

    val storedTrackingStartDate = MutableStateFlow<LocalDate?>(null)
    val trackingStartCalls = mutableListOf<LocalDate?>()
    val storedTrackingStartInitialized = MutableStateFlow(false)

    override val trackingStartDate: Flow<LocalDate?> = storedTrackingStartDate

    override val trackingStartInitialized: Flow<Boolean> = storedTrackingStartInitialized

    override suspend fun setThemeMode(mode: ThemeMode) {
        setCalls += mode
        stored.value = mode
    }

    override suspend fun setFontScale(scale: Float) {
        fontScaleCalls += scale
        storedFontScale.value = scale
    }

    override suspend fun setTrackingStartDate(date: LocalDate?) {
        trackingStartCalls += date
        storedTrackingStartDate.value = date
    }

    override suspend fun markTrackingStartInitialized() {
        storedTrackingStartInitialized.value = true
    }
}

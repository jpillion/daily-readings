package com.jpillion.dailyreadingplanner.testing

import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.domain.model.BibleProvider
import com.jpillion.dailyreadingplanner.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import java.time.LocalTime

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

    // --- S12: reminders. ---

    val storedReminderEnabled = MutableStateFlow(false)
    val reminderEnabledCalls = mutableListOf<Boolean>()
    val storedReminderTime = MutableStateFlow(SettingsRepository.DEFAULT_REMINDER_TIME)
    val reminderTimeCalls = mutableListOf<LocalTime>()

    override val reminderEnabled: Flow<Boolean> = storedReminderEnabled

    override val reminderTime: Flow<LocalTime> = storedReminderTime

    override suspend fun setReminderEnabled(enabled: Boolean) {
        reminderEnabledCalls += enabled
        storedReminderEnabled.value = enabled
    }

    override suspend fun setReminderTime(time: LocalTime) {
        reminderTimeCalls += time
        storedReminderTime.value = time
    }

    // --- S13: bible provider. ---

    val storedBibleProvider = MutableStateFlow(BibleProvider.DEFAULT)
    val bibleProviderCalls = mutableListOf<BibleProvider>()

    override val bibleProvider: Flow<BibleProvider> = storedBibleProvider

    override suspend fun setBibleProvider(provider: BibleProvider) {
        bibleProviderCalls += provider
        storedBibleProvider.value = provider
    }

    // --- S15: streak visibility. ---

    val storedShowStreaks = MutableStateFlow(false)
    val showStreaksCalls = mutableListOf<Boolean>()

    override val showStreaks: Flow<Boolean> = storedShowStreaks

    override suspend fun setShowStreaks(show: Boolean) {
        showStreaksCalls += show
        storedShowStreaks.value = show
    }

    // --- VD-T7/T10: first-run reading-destination question + one-time upgrade note markers. ---

    val storedReadingDestinationPromptCompleted = MutableStateFlow(false)
    var readingDestinationPromptCompletedCalls = 0

    override val readingDestinationPromptCompleted: Flow<Boolean> = storedReadingDestinationPromptCompleted

    override suspend fun markReadingDestinationPromptCompleted() {
        readingDestinationPromptCompletedCalls += 1
        storedReadingDestinationPromptCompleted.value = true
    }

    val storedUpgradeNoteShown = MutableStateFlow(false)
    var upgradeNoteShownCalls = 0

    override val upgradeNoteShown: Flow<Boolean> = storedUpgradeNoteShown

    override suspend fun markUpgradeNoteShown() {
        upgradeNoteShownCalls += 1
        storedUpgradeNoteShown.value = true
    }
}

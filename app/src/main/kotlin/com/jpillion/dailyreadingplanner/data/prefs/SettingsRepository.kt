package com.jpillion.dailyreadingplanner.data.prefs

import com.jpillion.dailyreadingplanner.domain.model.BibleProvider
import com.jpillion.dailyreadingplanner.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime

/**
 * User-settings persistence (ESpec §5.5 + S8 + S10): theme mode, text-size scale, and the
 * tracking start date. (Renamed from ThemeRepository in S10 — the stored DataStore file and
 * keys are unchanged.) Defaults: [ThemeMode.SYSTEM], [DEFAULT_FONT_SCALE], and an unset
 * (null) tracking start date.
 */
interface SettingsRepository {
    val themeMode: Flow<ThemeMode>

    /**
     * App-wide text scale factor (S8, D-S8-5), multiplied on top of the system font scale.
     * Always within [MIN_FONT_SCALE]..[MAX_FONT_SCALE]. Does not affect the widget (D-S7-3:
     * launcher surfaces follow system settings).
     */
    val fontScale: Flow<Float>

    /**
     * Tracking start date (S10, docs/features/tracking-start-date.md): days strictly before
     * this date are never classified MISSED — they are neutral in the picker and excluded
     * from missed/streak computation (R-STREAK-5). `null` = unset = track everything.
     * Start-date-inclusive: the start date itself IS tracked.
     */
    val trackingStartDate: Flow<LocalDate?>

    /**
     * One-time first-run marker for the tracking-start default (D-S10-1, spec §3 option B).
     * Kept separate from [trackingStartDate] so a user who deliberately clears the date to
     * null is not re-defaulted on the next launch.
     */
    val trackingStartInitialized: Flow<Boolean>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setFontScale(scale: Float)

    /** Persists the tracking start date; `null` clears it back to "track everything". */
    suspend fun setTrackingStartDate(date: LocalDate?)

    /** Marks the one-time tracking-start default as having run (never unset). */
    suspend fun markTrackingStartInitialized()

    /**
     * Daily reminder opt-in (S12, PRD R-REM-1/2): off by default — the app is silent until
     * the user asks. One reminder per day, no per-weekday schedules.
     */
    val reminderEnabled: Flow<Boolean>

    /**
     * The local time of day the daily reminder fires (S12, R-REM-2). Stored as minute-of-day
     * (timezone-free wall-clock semantics: "8:00 wherever the device is"). Defaults to
     * [DEFAULT_REMINDER_TIME]; only meaningful while [reminderEnabled] is true.
     */
    val reminderTime: Flow<LocalTime>

    suspend fun setReminderEnabled(enabled: Boolean)

    suspend fun setReminderTime(time: LocalTime)

    /**
     * The KJV destination reading taps open (S13, docs/features/bible-app-links.md).
     * Defaults to [BibleProvider.DEFAULT] (Blue Letter Bible) — existing users see zero
     * behavior change; unknown stored ids degrade to the default, never crash.
     */
    val bibleProvider: Flow<BibleProvider>

    suspend fun setBibleProvider(provider: BibleProvider)

    /**
     * Whether the streak stats are shown (S15, D-S15-5; default flipped OFF in S18 —
     * streaks are opt-in, owner decision). Off hides the
     * current/longest streak rows in the main-screen stats panel — year and per-stream
     * progress always remain. Display-only: streak *data* is derived live either way.
     */
    val showStreaks: Flow<Boolean>

    suspend fun setShowStreaks(show: Boolean)

    companion object {
        const val MIN_FONT_SCALE = 0.85f
        const val MAX_FONT_SCALE = 1.5f
        const val DEFAULT_FONT_SCALE = 1.0f

        /** D-S12-5: the pre-filled reminder time on first enable — one tap away from editing. */
        val DEFAULT_REMINDER_TIME: LocalTime = LocalTime.of(8, 0)
    }
}

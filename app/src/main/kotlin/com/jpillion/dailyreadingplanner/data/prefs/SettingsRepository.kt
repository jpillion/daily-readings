package com.jpillion.dailyreadingplanner.data.prefs

import com.jpillion.dailyreadingplanner.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

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

    companion object {
        const val MIN_FONT_SCALE = 0.85f
        const val MAX_FONT_SCALE = 1.5f
        const val DEFAULT_FONT_SCALE = 1.0f
    }
}

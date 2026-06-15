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
     * Persistent (ongoing) tray notification opt-in (S21; default flipped ON in S22 per the owner,
     * amending D-S22-5). When on, a silent, non-dismissible notification listing today's three
     * readings sits in the tray and refreshes to the new day at 01:00 local time. Separate from
     * [reminderEnabled] (the popup reminder). Absent key = true (fresh installs are on); an
     * explicitly stored choice survives. On a fresh API 33+ install the launch-time
     * POST_NOTIFICATIONS request is what lets the on-by-default notification actually post.
     */
    val persistentNotificationEnabled: Flow<Boolean>

    suspend fun setPersistentNotificationEnabled(enabled: Boolean)

    /**
     * The KJV destination reading taps open (S13, docs/features/bible-app-links.md).
     * Defaults to [BibleProvider.DEFAULT] (Blue Letter Bible) — existing users see zero
     * behavior change; unknown stored ids degrade to the default, never crash.
     */
    val bibleProvider: Flow<BibleProvider>

    suspend fun setBibleProvider(provider: BibleProvider)

    /**
     * Whether the first-run reading-destination question has been answered (V3, D-V3-19).
     * Gates the one-time first-run step that asks in-app vs. external; absent = never asked.
     * In-app is NEVER a silent default — the marker is set only with the user's answer.
     */
    val readingDestinationPromptCompleted: Flow<Boolean>

    suspend fun markReadingDestinationPromptCompleted()

    /**
     * Whether the one-time upgrade note ("the in-app Bible is here") has been shown (V3,
     * OQ-2). Existing users keep their external provider; this note is purely informational
     * and shows once. Absent = not yet shown.
     */
    val upgradeNoteShown: Flow<Boolean>

    suspend fun markUpgradeNoteShown()

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

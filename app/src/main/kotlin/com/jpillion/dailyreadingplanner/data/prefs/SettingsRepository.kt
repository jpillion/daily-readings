package com.jpillion.dailyreadingplanner.data.prefs

import com.jpillion.dailyreadingplanner.bible.domain.model.BibleVersion
import com.jpillion.dailyreadingplanner.domain.model.ExternalBibleApp
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestinationMode
import com.jpillion.dailyreadingplanner.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

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
     * WHERE a reading tap goes (Sprint K, D-23-1): the in-app reader vs. an external Bible
     * app/site — the orthogonal axis to [externalBibleApp]. Stored under the new
     * `reading_destination_mode` key. Pre-Sprint-K installs are migrated from the legacy
     * `bible_provider` value: a stored `IN_APP` ⇒ [ReadingDestinationMode.IN_APP], anything
     * else (incl. absent) ⇒ [ReadingDestinationMode.EXTERNAL] (BLB-in-browser, the historical
     * default) — so existing users see zero behavior change. Corrupt ⇒ [ReadingDestinationMode.DEFAULT].
     */
    val readingDestinationMode: Flow<ReadingDestinationMode>

    suspend fun setReadingDestinationMode(mode: ReadingDestinationMode)

    /**
     * WHICH external Bible app/site a reading tap opens when the mode is
     * [ReadingDestinationMode.EXTERNAL] (S13, docs/features/bible-app-links.md). Stored under
     * the legacy `bible_provider` key (so the four external names round-trip unchanged from
     * before Sprint K). Defaults to [ExternalBibleApp.DEFAULT] (Blue Letter Bible). The legacy
     * `IN_APP` value (no longer a member) is read as the default here — the in-app destination
     * lives entirely in [readingDestinationMode], so the remembered external app survives a
     * round-trip through the in-app mode. Unknown stored ids degrade to the default, never crash.
     */
    val externalBibleApp: Flow<ExternalBibleApp>

    suspend fun setExternalBibleApp(app: ExternalBibleApp)

    /**
     * Sprint 00R §2 step 3 — which text version the in-app reader shows (KJV bundled, NKJV/NASB
     * from the proxy). Defaults to [BibleVersion.DEFAULT] (KJV), so an upgrader and anyone who
     * never opens the version selector reads exactly the offline bundled text they read before.
     *
     * Orthogonal to [externalBibleApp] and [readingDestinationMode]: this is what the *in-app*
     * reader renders, not where a tap sends the user. Unknown stored codes degrade to KJV rather
     * than failing (the D-S13-4 idiom), so a removed or renamed version can never brick the reader.
     */
    val selectedBibleVersion: Flow<BibleVersion>

    suspend fun setSelectedBibleVersion(version: BibleVersion)

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

    /**
     * The user's selected plan id (alt-schedules D-ALT-16). Stored under `selected_plan`. Absent
     * key ⇒ the flagship default ([com.jpillion.dailyreadingplanner.data.plan.PlanRegistry.DEFAULT_PLAN_ID]),
     * so every existing install is on the Bible Companion invisibly (FR-ALT-2). This returns the
     * RAW stored id; degrading an unknown id (e.g. a plan removed in a later build) to the default
     * is [com.jpillion.dailyreadingplanner.data.plan.ActivePlanRepository]'s job (it knows the
     * registry). Mirrors the `bible_provider` absent/corrupt idiom: never crash.
     */
    val selectedPlanId: Flow<String>

    suspend fun setSelectedPlanId(id: String)

    companion object {
        const val MIN_FONT_SCALE = 0.85f
        const val MAX_FONT_SCALE = 1.5f
        const val DEFAULT_FONT_SCALE = 1.0f

        /** D-S12-5: the pre-filled reminder time on first enable — one tap away from editing. */
        val DEFAULT_REMINDER_TIME: LocalTime = LocalTime(8, 0)
    }
}

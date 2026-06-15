package com.jpillion.dailyreadingplanner.domain.model

/**
 * Sprint K (D-23-1) — WHERE a reading tap goes, as an axis orthogonal to *which* external app.
 *
 * Sprint 13–D conflated this with the app choice: a single `BibleProvider`/`bible_provider`
 * value carried both "read in the app" (the `IN_APP` member) and "open BLB/Gateway/…". The owner's
 * Settings redesign separates the two: a segmented toggle picks the *mode* (in this app vs. an
 * external Bible app), and an external-app dropdown picks [ExternalBibleApp] only when the mode is
 * [EXTERNAL]. The external app choice is *remembered* while the mode is [IN_APP], so a round-trip
 * through the toggle restores it (no silent reset to the default).
 *
 * Persistence: stored under the new DataStore key `reading_destination_mode` as the enum NAME —
 * never rename a constant without a settings migration. Absent/corrupt ⇒ [DEFAULT].
 */
enum class ReadingDestinationMode {
    /** The in-app KJV reader (V3) renders the whole portion natively — no app/URL. */
    IN_APP,

    /** A reading tap leaves for the user's chosen [ExternalBibleApp] (web Custom Tab or app intent). */
    EXTERNAL,

    ;

    companion object {
        /**
         * Pre-Sprint-K installs and fresh installs both resolve to [EXTERNAL] (the historical
         * default destination is Blue Letter Bible in a browser) — see
         * `SettingsRepository.readingDestinationMode` for the legacy `bible_provider == IN_APP`
         * migration that overrides this to [IN_APP].
         */
        val DEFAULT: ReadingDestinationMode = EXTERNAL

        /** Stored-id lookup; unknown/corrupt stored values degrade to [DEFAULT], never crash. */
        fun fromStored(stored: String?): ReadingDestinationMode = entries.firstOrNull { it.name == stored } ?: DEFAULT
    }
}

package com.jpillion.dailyreadingplanner.domain

/**
 * S-L (D-L-2) — the load-bearing pure rule for the in-app update notice: given the running
 * build's version code and the version code Play reports available, decide whether to surface
 * the flexible-update prompt at all. PATCH-only bumps are silent by design (the
 * new-version-notice exploration G2: a bugfix release must never interrupt the reader); a
 * MINOR-or-higher change prompts.
 *
 * The version scheme is `versionCode = MAJOR*10000 + MINOR*100 + PATCH` (D-S9-3), so the
 * "minor-or-higher digits" are exactly `versionCode / 100` (integer division drops the two
 * PATCH digits). A prompt is warranted iff:
 *   - Play actually offers an allowed flexible update ([UpdateAvailabilitySignal.UPDATE_AVAILABLE]), AND
 *   - Play's available code is strictly newer (`available > current`), AND
 *   - the minor-or-higher digits differ (`available / 100 != current / 100`).
 *
 * This is a pure total function over the inputs — no Play SDK types, no Android, no I/O — so it
 * is exhaustively unit-pinnable. [UpdateAvailabilitySignal] models only the two outcomes this
 * rule cares about (a newer version is offered, or nothing is); mapping the Play
 * `UpdateAvailability`/`isUpdateTypeAllowed` surface onto it lives in the data seam, never here.
 */
enum class UpdateAvailabilitySignal {
    /** Play reports a newer version is available AND the flexible flow is allowed. */
    UPDATE_AVAILABLE,

    /** No update offered (up to date, unknown, dev-triggered, or flexible not allowed). */
    NONE,
}

object UpdatePromptDecision {
    /** Drops the two PATCH digits of a D-S9-3 version code, leaving the MAJOR*100 + MINOR part. */
    private const val PATCH_DIGITS_DIVISOR = 100

    /**
     * True iff a flexible-update prompt should be shown.
     *
     * @param currentVersionCode the running build's `versionCode`.
     * @param availableVersionCode the `versionCode` Play reports available.
     * @param signal whether Play is offering an allowed flexible update at all.
     *
     * Returns false when there is no available update (signal == NONE), when the available code
     * is not strictly newer, or when only the PATCH digit changed.
     */
    fun shouldPrompt(
        currentVersionCode: Int,
        availableVersionCode: Int,
        signal: UpdateAvailabilitySignal,
    ): Boolean {
        if (signal != UpdateAvailabilitySignal.UPDATE_AVAILABLE) return false
        if (availableVersionCode <= currentVersionCode) return false
        return availableVersionCode / PATCH_DIGITS_DIVISOR != currentVersionCode / PATCH_DIGITS_DIVISOR
    }
}

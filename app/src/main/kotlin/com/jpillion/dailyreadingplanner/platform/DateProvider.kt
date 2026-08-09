package com.jpillion.dailyreadingplanner.platform

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * The app's notion of "now".
 *
 * Everything date-anchored resolves through this — never through a global clock — so that tests
 * can pin a date and the app has exactly ONE place that decides which calendar day the user is in.
 *
 * [today] is the user's *local* calendar date. Two users in different zones legitimately see
 * different readings at the same instant: that is the intended behaviour of a date-anchored plan,
 * and it is why this interface exposes a date rather than an instant to its callers.
 *
 * Implementations are cheap and side-effect-free. [today] may be called from a composable.
 */
interface DateProvider {
    /** The user's current local calendar date. */
    fun today(): LocalDate

    /** The current instant, for timestamping a mark (`readAtEpochMillis`). */
    fun now(): Instant

    /** The zone [today] is resolved in. Exposed so date arithmetic can be explicit, not ambient. */
    val timeZone: TimeZone
}

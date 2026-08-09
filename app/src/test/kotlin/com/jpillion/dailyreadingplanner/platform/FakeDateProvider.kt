package com.jpillion.dailyreadingplanner.platform

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.time.Instant

/**
 * The one [DateProvider] test double (p1-02), replacing the ~40 `Clock.fixed(...)` constructions
 * the suite used to carry.
 *
 * [today] is a `var` because several tests advance the date on a live subscription — the shape the
 * old `Clock` fakes had. [now] defaults to the start of [today] in [timeZone], which is what the
 * tests that only care about "which day is it" need; a test that asserts on a specific
 * `readAtEpochMillis` sets [now] explicitly rather than growing a second constructor parameter.
 *
 * [timeZone] defaults to UTC so a date pinned here means the same day everywhere the suite runs.
 */
class FakeDateProvider(
    var today: LocalDate,
    override val timeZone: TimeZone = TimeZone.UTC,
    var now: Instant? = null,
) : DateProvider {
    override fun today(): LocalDate = today

    override fun now(): Instant = now ?: today.atStartOfDayIn(timeZone)
}

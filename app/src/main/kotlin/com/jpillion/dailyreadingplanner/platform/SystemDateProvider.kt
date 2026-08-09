package com.jpillion.dailyreadingplanner.platform

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The Android [DateProvider] (p1-02): the system clock read in the device's current zone.
 *
 * **A literal transcription of what `java.time.Clock.systemDefaultZone()` did**, which is what the
 * app injected at 13 sites before this seam existed:
 *
 * - `LocalDate.now(clock)` was "the instant now, resolved in the clock's zone, as a date" — which
 *   is exactly [today] here, and `Clock.systemDefaultZone()` supplied the *system default* zone.
 * - `clock.millis()` was "the instant now, in epoch millis" — [now] plus `toEpochMilliseconds()`.
 *
 * [timeZone] is read on **every** access rather than captured once, matching
 * `Clock.systemDefaultZone()`, whose returned clock resolves `ZoneId.systemDefault()` per call.
 * A stored `val` would freeze the zone at construction and change behaviour for a user who flies
 * across a boundary while the process is alive — the exact case ADR-0009 says this seam owns.
 */
class SystemDateProvider
    @Inject
    constructor() : DateProvider {
        override fun today(): LocalDate = Clock.System.todayIn(timeZone)

        override fun now(): Instant = Clock.System.now()

        override val timeZone: TimeZone
            get() = TimeZone.currentSystemDefault()
    }

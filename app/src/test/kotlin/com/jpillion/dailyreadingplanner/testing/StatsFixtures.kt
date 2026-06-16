package com.jpillion.dailyreadingplanner.testing

import com.jpillion.dailyreadingplanner.domain.model.ReadingStats
import com.jpillion.dailyreadingplanner.domain.model.StreamDescriptor
import com.jpillion.dailyreadingplanner.domain.model.StripDayState
import com.jpillion.dailyreadingplanner.domain.model.YearStrips

/**
 * The Bible-Companion stream identity (N=3) used by the stats/strip/day UI tests — the parity
 * baseline. After the Sprint-C `Stream`-enum retirement (D-ALT-5), stats/strips are keyed by stream
 * NUMBER and carry the active plan's ordered [StreamDescriptor]s; these helpers keep that shape in
 * one place so the UI pins read cleanly. A test exercising N≠3 builds its own descriptor list.
 */
val bcStreamDescriptors =
    listOf(
        StreamDescriptor(1, "Law & History"),
        StreamDescriptor(2, "Psalms & Prophecy"),
        StreamDescriptor(3, "New Testament"),
    )

/** A Bible-Companion-shaped [ReadingStats] (1,095 / 365 denominators, 3 streams). */
fun bcReadingStats(
    currentStreakDays: Int = 0,
    longestStreakDays: Int = 0,
    yearReadCount: Int = 0,
    streamReadCounts: Map<Int, Int> = mapOf(1 to 0, 2 to 0, 3 to 0),
    streams: List<StreamDescriptor> = bcStreamDescriptors,
    yearTotalReadings: Int = streams.size * 365,
    streamTotalDays: Int = 365,
): ReadingStats =
    ReadingStats(
        currentStreakDays = currentStreakDays,
        longestStreakDays = longestStreakDays,
        yearReadCount = yearReadCount,
        streamReadCounts = streamReadCounts,
        streams = streams,
        yearTotalReadings = yearTotalReadings,
        streamTotalDays = streamTotalDays,
    )

/** A Bible-Companion-shaped [YearStrips] whose every stream's day states come from [statesFor]. */
fun bcYearStrips(
    year: Int = 2026,
    todayIndex: Int? = null,
    streams: List<StreamDescriptor> = bcStreamDescriptors,
    statesFor: (StreamDescriptor) -> List<StripDayState>,
): YearStrips =
    YearStrips(
        year = year,
        todayIndex = todayIndex,
        dayStates = streams.associate { it.number to statesFor(it) },
        streams = streams,
    )

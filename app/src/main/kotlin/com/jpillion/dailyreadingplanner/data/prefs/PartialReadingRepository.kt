package com.jpillion.dailyreadingplanner.data.prefs

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * The cosmetic partial-check cache (D-SEG-4): which segments of a multi-passage reading the user
 * has checked but not yet completed.
 *
 * **This is a cache, NOT a progress record (D-SEG-3).** The Room progress DB still stores exactly
 * one mark per (plan, date, stream) and is completely untouched by this store. Nothing outside the
 * day screen reads these tokens: stats, streaks, year strips, picker dots, the widget and the
 * reminder / persistent notification all continue to read the Room stream marks only, so a
 * partially-read reading counts as **not read** everywhere else — which is the intent.
 *
 * Consequences of being a cache, all deliberate:
 * - It is pruned on write (D-SEG-5) — old tokens are dropped, so growth is bounded.
 * - A malformed token (from a future or older build) is dropped, never surfaced.
 * - Losing it loses nothing real: the underlying reading progress lives in Room.
 */
interface PartialReadingRepository {
    /** Every cached partial-segment token, live. Raw tokens; callers decode with [PartialSegmentToken]. */
    val partialSegments: Flow<Set<String>>

    /**
     * Replaces the cached partial segments for exactly ([planId], [date], [streamNumber]) with
     * [segmentIndexes]. An empty set clears them. Tokens for other plans/dates/streams are untouched.
     *
     * Every write also prunes the whole set (D-SEG-5): tokens older than
     * [PartialReadingRepositoryImpl.RETENTION_DAYS] days and tokens that fail to parse are dropped.
     */
    suspend fun setPartialSegments(
        planId: String,
        date: LocalDate,
        streamNumber: Int,
        segmentIndexes: Set<Int>,
    )
}

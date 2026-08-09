package com.jpillion.dailyreadingplanner.testing

import com.jpillion.dailyreadingplanner.data.prefs.PartialReadingRepository
import com.jpillion.dailyreadingplanner.data.prefs.PartialSegmentToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate

/**
 * In-memory [PartialReadingRepository] for use-case tests (sprint-00P, D-SEG-4).
 *
 * Mirrors the real impl's **replace-this-triple** semantics — a write replaces exactly this
 * (plan, date, stream)'s tokens and leaves every other plan/date/stream alone — but deliberately
 * does NOT prune (D-SEG-5 retention is pinned against the real impl in
 * `PartialReadingRepositoryImplTest`; re-implementing it here would be a second home for the rule).
 *
 * Records the ordered sequence of writes it receives ([writes]) and appends each one to the shared
 * [writeLog] timeline, so a test that also records the Room mark writes into the same list can pin
 * the load-bearing mark-vs-tokens **write order**.
 */
class FakePartialReadingRepository(
    /**
     * Shared ordered timeline of store writes. Pass the same list to a recording progress
     * repository to observe the interleaving of the two stores.
     */
    val writeLog: MutableList<String> = mutableListOf(),
) : PartialReadingRepository {
    val stored = MutableStateFlow<Set<String>>(emptySet())

    override val partialSegments: Flow<Set<String>> = stored

    /** Every [setPartialSegments] call, in order. */
    val writes = mutableListOf<Write>()

    data class Write(
        val planId: String,
        val date: LocalDate,
        val streamNumber: Int,
        val segmentIndexes: Set<Int>,
    )

    override suspend fun setPartialSegments(
        planId: String,
        date: LocalDate,
        streamNumber: Int,
        segmentIndexes: Set<Int>,
    ) {
        writes += Write(planId, date, streamNumber, segmentIndexes)
        writeLog += TOKENS_WRITE
        val epochDay = date.toEpochDays()
        val others =
            stored.value.filterNot { token ->
                val parsed = PartialSegmentToken.parse(token)
                parsed != null &&
                    parsed.planId == planId &&
                    parsed.epochDay == epochDay &&
                    parsed.streamNumber == streamNumber
            }
        stored.value =
            (
                others +
                    segmentIndexes.map { index ->
                        PartialSegmentToken.encode(planId, epochDay, streamNumber, index)
                    }
            ).toSet()
    }

    /** Seeds raw tokens directly — including deliberately malformed ones. */
    fun seedRaw(vararg tokens: String) {
        stored.value = stored.value + tokens
    }

    /** The stored segment indexes for exactly (planId, date, streamNumber). */
    fun segmentsFor(
        planId: String,
        date: LocalDate,
        streamNumber: Int,
    ): Set<Int> =
        stored.value
            .mapNotNull { PartialSegmentToken.parse(it) }
            .filter { it.planId == planId && it.epochDay == date.toEpochDays() && it.streamNumber == streamNumber }
            .map { it.segmentIndex }
            .toSet()

    companion object {
        /** [writeLog] entry for a token-set write. */
        const val TOKENS_WRITE = "tokens"
    }
}

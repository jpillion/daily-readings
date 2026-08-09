package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.plan.ActivePlanRepository
import com.jpillion.dailyreadingplanner.data.prefs.PartialReadingRepository
import com.jpillion.dailyreadingplanner.data.prefs.PartialSegmentToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import javax.inject.Inject

/**
 * The live partial-check cache for one date, decoded (D-SEG-4, sprint-00P) — the read half of the
 * day screen's tri-state checks. The write half is [ToggleSegmentCheckUseCase] /
 * [MarkSegmentReadOnOpenUseCase]; the state itself is derived by `SegmentCheckPolicy.stateFor`.
 *
 * Keyed on the ACTIVE plan (D-ALT-17): the flow combines `activePlanId` with the raw token set, so
 * switching plans re-emits with that plan's partials and never leaks another plan's tokens.
 * Nothing is cached here — the two source flows are already live, and a stale snapshot is exactly
 * the drift this seam exists to avoid.
 *
 * D-SEG-3: this is a cosmetic cache only. The real progress mark stays in Room, untouched.
 */
class GetPartialSegmentsUseCase
    @Inject
    constructor(
        private val partialReadingRepository: PartialReadingRepository,
        private val activePlanRepository: ActivePlanRepository,
    ) {
        /**
         * `streamNumber -> the segment indexes currently PARTIAL for [date]` in the active plan.
         *
         * A stream with no partial segments is **absent** from the map (callers use
         * `?: emptySet()`), and unparseable tokens are silently dropped — the cache is
         * self-healing by construction (see [PartialSegmentToken.parse]).
         */
        operator fun invoke(date: LocalDate): Flow<Map<Int, Set<Int>>> {
            val epochDay = date.toEpochDays()
            return combine(
                activePlanRepository.activePlanId,
                partialReadingRepository.partialSegments,
            ) { planId, tokens ->
                partialSegmentsByStream(tokens, planId, epochDay)
            }
        }
    }

/**
 * THE decode-and-filter for the partial-check cache: raw [tokens] → `streamNumber -> segment
 * indexes`, keeping only tokens belonging to exactly ([planId], [epochDay]).
 *
 * One home for the rule, shared by the read use case above and both write use cases, so the
 * "which tokens are mine?" question can never be answered two different ways. Tokens that fail to
 * parse are dropped, never surfaced.
 */
internal fun partialSegmentsByStream(
    tokens: Set<String>,
    planId: String,
    epochDay: Long,
): Map<Int, Set<Int>> =
    tokens
        .mapNotNull { PartialSegmentToken.parse(it) }
        .filter { parsed -> parsed.planId == planId && parsed.epochDay == epochDay }
        .groupBy({ it.streamNumber }, { it.segmentIndex })
        .mapValues { (_, indexes) -> indexes.toSet() }

/** The current partial segment indexes for exactly ([planId], [date], [streamNumber]). */
internal suspend fun PartialReadingRepository.currentPartials(
    planId: String,
    date: LocalDate,
    streamNumber: Int,
): Set<Int> = partialSegmentsByStream(partialSegments.first(), planId, date.toEpochDays())[streamNumber] ?: emptySet()

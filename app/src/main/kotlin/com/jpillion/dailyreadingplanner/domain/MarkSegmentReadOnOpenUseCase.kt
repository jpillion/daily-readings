package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.plan.ActivePlanRepository
import com.jpillion.dailyreadingplanner.data.prefs.PartialReadingRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

/**
 * Applies the "opening a reading marks it read" side effect at SEGMENT level (sprint-00P over
 * Sprint-00O's D-O-1): runs the pure [SegmentCheckPolicy.onOpen] and performs its writes.
 *
 * **One-way SET, never an unmark** — the D-O-1 invariant, preserved exactly:
 * - the Room write is delegated to [MarkReadOnOpenUseCase] (one home for "mark read on open"), and
 *   is issued **only when the outcome sets the mark**. When the outcome's mark is `false` the mark
 *   was already `false` (an unmarked reading whose tapped segment merely became/stayed PARTIAL);
 *   calling a mark-read use case there would be wrong, and calling a toggle-to-false use case
 *   would be an unmark — so no Room write is issued at all.
 * - an already-COMPLETE reading and an already-PARTIAL segment come back unchanged from the policy.
 *
 * At N == 1 this sets the real mark exactly as today and never writes a token.
 */
class MarkSegmentReadOnOpenUseCase
    @Inject
    constructor(
        private val markReadOnOpen: MarkReadOnOpenUseCase,
        private val partialReadingRepository: PartialReadingRepository,
        private val activePlanRepository: ActivePlanRepository,
    ) {
        suspend operator fun invoke(
            date: LocalDate,
            streamNumber: Int,
            segmentIndex: Int,
            segmentCount: Int,
            streamMarked: Boolean,
        ) {
            val planId = activePlanRepository.activePlanId.first()
            val currentPartials = partialReadingRepository.currentPartials(planId, date, streamNumber)
            val outcome = SegmentCheckPolicy.onOpen(segmentIndex, segmentCount, streamMarked, currentPartials)

            // Same load-bearing write order as ToggleSegmentCheckUseCase (see its comment):
            // `SegmentCheckPolicy.stateFor` is mark-dominant and the UI observes both stores, so the
            // intermediate frame must render as the start or the end state, never a third thing.
            if (outcome.streamMarkRead) {
                // Completing: mark FIRST — intermediate = marked + stale tokens = COMPLETE = the end
                // state. Tokens first would flash the reading unchecked for a frame.
                markReadOnOpen(date, streamNumber)
                partialReadingRepository.setPartialSegments(planId, date, streamNumber, outcome.partialSegments)
            } else {
                // Mark stays false (it already was), so the token write is the only write and there
                // is no second store to sequence against.
                partialReadingRepository.setPartialSegments(planId, date, streamNumber, outcome.partialSegments)
            }
        }
    }

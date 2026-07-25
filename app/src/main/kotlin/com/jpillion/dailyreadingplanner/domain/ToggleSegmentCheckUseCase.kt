package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.data.plan.ActivePlanRepository
import com.jpillion.dailyreadingplanner.data.prefs.PartialReadingRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

/**
 * Applies a checkbox tap on one reading-card segment (sprint-00P): runs the pure
 * [SegmentCheckPolicy.onToggle] table and performs its two writes — the real Room stream mark and
 * the DataStore token set (D-SEG-3/D-SEG-4).
 *
 * The Room write is **delegated to [ToggleReadingUseCase]** rather than reaching for
 * `ProgressRepository` directly: "how a reading mark is written" (active-plan scoping included)
 * keeps exactly one home, and that use case's existing pins stay meaningful.
 *
 * At N == 1 this is byte-for-byte today's plain mark/unmark — the policy degrades to it with no
 * token ever written (pinned by test), so there is no single-segment branch here to drift.
 */
class ToggleSegmentCheckUseCase
    @Inject
    constructor(
        private val toggleReading: ToggleReadingUseCase,
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
            val outcome = SegmentCheckPolicy.onToggle(segmentIndex, segmentCount, streamMarked, currentPartials)

            // WRITE ORDER IS LOAD-BEARING. The mark lives in Room and the tokens in DataStore; the
            // day screen observes both, so the intermediate state between the two writes is
            // rendered. `SegmentCheckPolicy.stateFor` is mark-DOMINANT (a set mark shows COMPLETE
            // regardless of tokens), so the order below is chosen to make that intermediate frame
            // render as either the start state or the end state — never a third thing.
            if (outcome.streamMarkRead) {
                // Completing: mark FIRST. Intermediate = marked + stale tokens = COMPLETE = the end
                // state. (Tokens first would clear them while the mark is still false, flashing the
                // whole reading unchecked for a frame.)
                toggleReading(date, streamNumber, markRead = true)
                partialReadingRepository.setPartialSegments(planId, date, streamNumber, outcome.partialSegments)
            } else {
                // Un-completing / partial edit: tokens FIRST. Intermediate = still-marked + new
                // tokens = COMPLETE = the start state. (Mark first would drop to the new token set
                // with the mark already cleared, flashing "all unchecked" before the survivors
                // reappear as PARTIAL.)
                partialReadingRepository.setPartialSegments(planId, date, streamNumber, outcome.partialSegments)
                toggleReading(date, streamNumber, markRead = false)
            }
        }
    }

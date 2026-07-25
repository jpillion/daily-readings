package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.domain.model.ReadingCheckState

/**
 * The complete desired end-state for one (plan, date, stream) after an action (sprint-00P).
 *
 * [streamMarkRead] is the real Room progress mark (D-SEG-3, unchanged storage); [partialSegments]
 * is the FULL desired token set for that reading in the DataStore cache (D-SEG-4) — the applying
 * use case writes both, so this value is the whole truth, never a delta.
 */
data class SegmentCheckOutcome(
    val streamMarkRead: Boolean,
    val partialSegments: Set<Int>,
)

/**
 * THE transition policy for reading-card checks (sprint-00P) — pure, the single home of the table
 * in docs/features/reading-card-segments.md, and this sprint's primary mutation target. The I/O
 * (Room mark + DataStore token set) is performed by the use case that applies the returned
 * [SegmentCheckOutcome]; nothing here touches storage.
 *
 * | action | result |
 * |---|---|
 * | tap `UNCHECKED`, N>1, others not all checked | write token → `PARTIAL` |
 * | tap `UNCHECKED` that is the **last** one needed | clear all tokens **and** set the stream mark → all `COMPLETE` |
 * | tap `PARTIAL` | remove token → `UNCHECKED` |
 * | tap `COMPLETE` (stream marked read) | clear the mark; tapped segment → `UNCHECKED`, **all others → `PARTIAL`** |
 * | N == 1, any tap | toggle the real mark directly; never write a token |
 *
 * The N == 1 row is deliberately NOT a special case in [onToggle] — the general rules already
 * degrade to it (the last-one-needed branch fires immediately, and unmarking yields an empty
 * "all others" set), so single-segment behaviour is byte-for-byte today's by construction rather
 * than by a parallel branch that could drift. Pinned by test.
 */
object SegmentCheckPolicy {
    /**
     * The state one segment displays. [streamMarked] is the real Room mark for the reading;
     * [hasToken] is whether this segment's token is in the DataStore cache.
     *
     * D-SEG-4: at [segmentCount] == 1 the answer is [ReadingCheckState.UNCHECKED] regardless of
     * [hasToken] — PARTIAL is unreachable for a single-segment reading, and this defends against a
     * stale token left by an earlier plan revision that split the reading differently.
     */
    fun stateFor(
        streamMarked: Boolean,
        segmentCount: Int,
        hasToken: Boolean,
    ): ReadingCheckState =
        when {
            streamMarked -> ReadingCheckState.COMPLETE
            segmentCount == 1 -> ReadingCheckState.UNCHECKED
            hasToken -> ReadingCheckState.PARTIAL
            else -> ReadingCheckState.UNCHECKED
        }

    /**
     * Tapping the checkbox of segment [segmentIndex] of a [segmentCount]-segment reading whose real
     * mark is [streamMarked] and whose current token set is [partials]. Returns the desired end
     * state.
     */
    fun onToggle(
        segmentIndex: Int,
        segmentCount: Int,
        streamMarked: Boolean,
        partials: Set<Int>,
    ): SegmentCheckOutcome {
        requireSegment(segmentIndex, segmentCount)
        return when {
            // COMPLETE: clear the real mark; the tapped segment becomes UNCHECKED and every other
            // segment becomes PARTIAL (it was read — only the tapped one is being taken back).
            // At N == 1 this is (false, emptySet()) — the plain unmark.
            streamMarked ->
                SegmentCheckOutcome(
                    streamMarkRead = false,
                    partialSegments = (0 until segmentCount).toSet() - segmentIndex,
                )
            // PARTIAL: drop the token.
            segmentIndex in partials ->
                SegmentCheckOutcome(streamMarkRead = false, partialSegments = partials - segmentIndex)
            // UNCHECKED: add the token; if that completes the reading, clear ALL tokens and set the
            // real stream mark instead. At N == 1 the first tap is always the last one needed, so
            // this degrades to the plain mark with no token ever written.
            else -> {
                val next = partials + segmentIndex
                if (next.size == segmentCount) {
                    SegmentCheckOutcome(streamMarkRead = true, partialSegments = emptySet())
                } else {
                    SegmentCheckOutcome(streamMarkRead = false, partialSegments = next)
                }
            }
        }
    }

    /**
     * Tapping the card BODY of segment [segmentIndex] to open it — the Sprint-00O one-way SET
     * semantics, preserved: opening never unmarks. An already-`COMPLETE` reading and an already-
     * `PARTIAL` segment are left exactly as they are; an `UNCHECKED` segment takes the [onToggle]
     * path (→ PARTIAL, or completes the stream when it is the last one needed).
     */
    fun onOpen(
        segmentIndex: Int,
        segmentCount: Int,
        streamMarked: Boolean,
        partials: Set<Int>,
    ): SegmentCheckOutcome {
        requireSegment(segmentIndex, segmentCount)
        return when {
            streamMarked -> SegmentCheckOutcome(streamMarkRead = true, partialSegments = emptySet())
            segmentIndex in partials -> SegmentCheckOutcome(streamMarkRead = false, partialSegments = partials)
            else -> onToggle(segmentIndex, segmentCount, streamMarked, partials)
        }
    }

    private fun requireSegment(
        segmentIndex: Int,
        segmentCount: Int,
    ) {
        require(segmentIndex in 0 until segmentCount) {
            "segment index $segmentIndex out of 0..${segmentCount - 1}"
        }
    }
}

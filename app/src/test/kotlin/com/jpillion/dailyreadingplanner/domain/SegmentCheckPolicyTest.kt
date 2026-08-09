package com.jpillion.dailyreadingplanner.domain

import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.domain.model.ReadingCheckState
import org.junit.Test

/**
 * SEG-1 — every row of THE transition table (docs/features/reading-card-segments.md), plus the
 * N == 1 parity path and `stateFor`'s three branches. `SegmentCheckPolicy` is the sprint's primary
 * mutation target, so each pin below is deliberately narrow.
 *
 * Mutation targets these pins kill:
 *  - dropping the `segmentCount == 1` guard in `stateFor` → `state at N==1 ignores a stale token`
 *    reddens (the D-SEG-4 defence).
 *  - moving the `streamMarked` branch below the N==1 guard → `state is COMPLETE whenever the stream
 *    is marked` reddens at N == 1.
 *  - dropping the "all others become PARTIAL" set in the COMPLETE branch of `onToggle` →
 *    `tap COMPLETE unmarks the stream and leaves every OTHER segment PARTIAL` reddens.
 *  - flipping the last-one-needed test (`next.size == segmentCount`) → `tap the LAST unchecked
 *    segment completes the stream and clears all tokens` and/or `tap UNCHECKED with others still
 *    outstanding only writes a token` redden.
 *  - letting `onOpen` fall through to `onToggle` for a PARTIAL or COMPLETE segment → the two
 *    one-way-SET pins redden (Sprint-00O semantics: opening never unmarks).
 */
class SegmentCheckPolicyTest {
    // ---- stateFor -------------------------------------------------------------------------

    @Test
    fun `state is COMPLETE whenever the stream is marked - regardless of tokens or count`() {
        assertThat(SegmentCheckPolicy.stateFor(streamMarked = true, segmentCount = 1, hasToken = false))
            .isEqualTo(ReadingCheckState.COMPLETE)
        assertThat(SegmentCheckPolicy.stateFor(streamMarked = true, segmentCount = 3, hasToken = false))
            .isEqualTo(ReadingCheckState.COMPLETE)
        assertThat(SegmentCheckPolicy.stateFor(streamMarked = true, segmentCount = 3, hasToken = true))
            .isEqualTo(ReadingCheckState.COMPLETE)
    }

    @Test
    fun `state at N==1 ignores a stale token and is always UNCHECKED when unmarked`() {
        // D-SEG-4: PARTIAL is unreachable for a single-segment reading. A token left behind by an
        // earlier plan revision that split this reading differently must NOT resurrect it.
        assertThat(SegmentCheckPolicy.stateFor(streamMarked = false, segmentCount = 1, hasToken = true))
            .isEqualTo(ReadingCheckState.UNCHECKED)
        assertThat(SegmentCheckPolicy.stateFor(streamMarked = false, segmentCount = 1, hasToken = false))
            .isEqualTo(ReadingCheckState.UNCHECKED)
    }

    @Test
    fun `state at N greater than 1 is PARTIAL with a token and UNCHECKED without`() {
        assertThat(SegmentCheckPolicy.stateFor(streamMarked = false, segmentCount = 2, hasToken = true))
            .isEqualTo(ReadingCheckState.PARTIAL)
        assertThat(SegmentCheckPolicy.stateFor(streamMarked = false, segmentCount = 2, hasToken = false))
            .isEqualTo(ReadingCheckState.UNCHECKED)
    }

    // ---- onToggle: the transition table ---------------------------------------------------

    @Test
    fun `tap UNCHECKED with others still outstanding only writes a token`() {
        // Row 1: N>1, others not all checked -> PARTIAL.
        val outcome = SegmentCheckPolicy.onToggle(0, segmentCount = 3, streamMarked = false, partials = emptySet())

        assertThat(outcome).isEqualTo(SegmentCheckOutcome(streamMarkRead = false, partialSegments = setOf(0)))
    }

    @Test
    fun `tap UNCHECKED adds to an existing token set while any segment is outstanding`() {
        val outcome = SegmentCheckPolicy.onToggle(2, segmentCount = 4, streamMarked = false, partials = setOf(0, 1))

        assertThat(outcome).isEqualTo(SegmentCheckOutcome(streamMarkRead = false, partialSegments = setOf(0, 1, 2)))
    }

    @Test
    fun `tap the LAST unchecked segment completes the stream and clears all tokens`() {
        // Row 2: the last one needed -> clear every token AND set the real stream mark.
        val outcome = SegmentCheckPolicy.onToggle(2, segmentCount = 3, streamMarked = false, partials = setOf(0, 1))

        assertThat(outcome).isEqualTo(SegmentCheckOutcome(streamMarkRead = true, partialSegments = emptySet()))
    }

    @Test
    fun `tap PARTIAL removes just that token`() {
        // Row 3: PARTIAL -> UNCHECKED, the other tokens survive.
        val outcome = SegmentCheckPolicy.onToggle(1, segmentCount = 3, streamMarked = false, partials = setOf(0, 1))

        assertThat(outcome).isEqualTo(SegmentCheckOutcome(streamMarkRead = false, partialSegments = setOf(0)))
    }

    @Test
    fun `tap COMPLETE unmarks the stream and leaves every OTHER segment PARTIAL`() {
        // Row 4: the tapped segment is taken back; the rest were genuinely read, so they stay checked
        // (cosmetically) as PARTIAL.
        val outcome = SegmentCheckPolicy.onToggle(1, segmentCount = 3, streamMarked = true, partials = emptySet())

        assertThat(outcome).isEqualTo(SegmentCheckOutcome(streamMarkRead = false, partialSegments = setOf(0, 2)))
    }

    // ---- onToggle: the N == 1 parity path (falls out, no special case) ---------------------

    @Test
    fun `N==1 mark falls out of the general last-one-needed rule with no token written`() {
        // Row 5, half one: the FIRST tap is always the last one needed at N == 1, so the general
        // rule sets the real mark and writes nothing to the token cache — byte-for-byte today's
        // behaviour, with no n==1 branch in onToggle.
        val outcome = SegmentCheckPolicy.onToggle(0, segmentCount = 1, streamMarked = false, partials = emptySet())

        assertThat(outcome).isEqualTo(SegmentCheckOutcome(streamMarkRead = true, partialSegments = emptySet()))
    }

    @Test
    fun `N==1 unmark falls out of the general COMPLETE rule with an empty others set`() {
        // Row 5, half two: "all others become PARTIAL" is vacuous at N == 1 -> the plain unmark.
        val outcome = SegmentCheckPolicy.onToggle(0, segmentCount = 1, streamMarked = true, partials = emptySet())

        assertThat(outcome).isEqualTo(SegmentCheckOutcome(streamMarkRead = false, partialSegments = emptySet()))
    }

    // ---- onOpen: Sprint-00O one-way SET ----------------------------------------------------

    @Test
    fun `open an UNCHECKED segment sets it read exactly like a toggle`() {
        assertThat(SegmentCheckPolicy.onOpen(0, segmentCount = 3, streamMarked = false, partials = emptySet()))
            .isEqualTo(SegmentCheckOutcome(streamMarkRead = false, partialSegments = setOf(0)))
    }

    @Test
    fun `open the LAST unchecked segment completes the stream`() {
        assertThat(SegmentCheckPolicy.onOpen(2, segmentCount = 3, streamMarked = false, partials = setOf(0, 1)))
            .isEqualTo(SegmentCheckOutcome(streamMarkRead = true, partialSegments = emptySet()))
    }

    @Test
    fun `open a PARTIAL segment leaves it alone - never unmarks`() {
        assertThat(SegmentCheckPolicy.onOpen(1, segmentCount = 3, streamMarked = false, partials = setOf(0, 1)))
            .isEqualTo(SegmentCheckOutcome(streamMarkRead = false, partialSegments = setOf(0, 1)))
    }

    @Test
    fun `open a COMPLETE segment leaves the stream marked - never unmarks`() {
        assertThat(SegmentCheckPolicy.onOpen(1, segmentCount = 3, streamMarked = true, partials = emptySet()))
            .isEqualTo(SegmentCheckOutcome(streamMarkRead = true, partialSegments = emptySet()))
    }

    @Test
    fun `open at N==1 sets the real mark exactly as today`() {
        assertThat(SegmentCheckPolicy.onOpen(0, segmentCount = 1, streamMarked = false, partials = emptySet()))
            .isEqualTo(SegmentCheckOutcome(streamMarkRead = true, partialSegments = emptySet()))
    }

    // ---- guards ----------------------------------------------------------------------------

    @Test
    fun `an out-of-range segment index is a programming error on both entry points`() {
        for (index in listOf(-1, 3)) {
            val toggle =
                runCatching {
                    SegmentCheckPolicy.onToggle(index, segmentCount = 3, streamMarked = false, partials = emptySet())
                }
            assertThat(toggle.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)

            val open =
                runCatching {
                    SegmentCheckPolicy.onOpen(index, segmentCount = 3, streamMarked = false, partials = emptySet())
                }
            assertThat(open.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        }
    }
}

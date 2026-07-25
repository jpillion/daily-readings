package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.bible.domain.ConsecutiveChapterRuns
import com.jpillion.dailyreadingplanner.domain.model.Portion

/**
 * D-SEG-1 (sprint-00P) — THE segmentation rule: a **segment** (= one reading card) is a maximal run
 * of a portion's refs sharing the same book with consecutive ascending chapters.
 *
 * - A **book change splits** (Isaiah 39 → Psalms 76 = two segments — the Ticket-1 repro).
 * - A **chapter gap splits** (Genesis 3–4 then Genesis 8–10 = two segments).
 * - A **verse window does NOT split**: M'Cheyne 02/28 stream 1 (`Exodus 11` + `Exodus 12:1–21`) is
 *   ONE segment — same book, consecutive chapters — whose card text reads
 *   "Exodus 11; Exodus 12:1–21".
 *
 * This delegates to [ConsecutiveChapterRuns] — the existing grouper already used by
 * `data/reference/ProviderUrlBuilder` for external URLs. **One home, no second grouper** (the
 * one-home discipline of D-V3-10): card boundaries and external-URL grouping are the same question
 * and must not drift.
 *
 * Deliberately distinct from `ui/day/ReadingFormatter`'s *private* `consecutiveRuns`, which
 * additionally breaks on verse windows. That is a different concern — display **within** a card
 * (so one card can read "Exodus 11; Exodus 12:1–21") — and stays untouched.
 *
 * D-SEG-2: because every segment is same-book + consecutive ascending chapters, it is by
 * construction a contiguous ascending global-chapter run, so
 * `bible/ui/reader/ReadingPagerIndex(segment)` can never throw. That invariant is pinned over the
 * real bundled assets by `PlanSegmentGateTest` — the gate that makes Ticket 1 non-recurring.
 */
object ReadingSegments {
    /**
     * Splits [portion] into its segments, preserving order. Each segment is a [Portion] carrying the
     * original [Portion.streamNumber] and that run's refs; a single-run portion yields a one-element
     * list containing an equal portion. Concatenating the segments' refs reproduces
     * [Portion.refs] exactly.
     */
    fun segmentsOf(portion: Portion): List<Portion> =
        ConsecutiveChapterRuns.group(portion.refs).map { run ->
            Portion(streamNumber = portion.streamNumber, refs = run)
        }
}

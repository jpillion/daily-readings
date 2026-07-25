package com.jpillion.dailyreadingplanner.domain.model

/**
 * The tri-state check shown on one reading card (= one D-SEG-1 segment), sprint-00P.
 *
 * - [UNCHECKED] — no mark, no token.
 * - [PARTIAL] — a **cosmetic** per-segment check (D-SEG-4). It lives ONLY in the DataStore
 *   `partial_reading_segments` token cache, never in Room; everything outside the day screen
 *   (stats, streaks, year strips, picker dots, widget, reminders, day completion) reads the real
 *   stream mark and therefore treats a partially-read reading as **not read** — the intended
 *   behaviour (D-SEG-3: progress storage is unchanged, no Room migration).
 * - [COMPLETE] — the real persisted stream mark in the Room progress DB (one mark per
 *   (plan, date, stream), exactly as today). When it is set, EVERY segment of that reading shows
 *   [COMPLETE] and the token cache for that reading is empty.
 *
 * D-SEG-4: [PARTIAL] is **unreachable when a reading has exactly one segment** — at N == 1 the
 * behaviour is byte-for-byte today's plain mark/unmark, and `SegmentCheckPolicy.stateFor` defends
 * against a stale token left behind by an earlier plan revision by ignoring tokens at N == 1.
 */
enum class ReadingCheckState {
    UNCHECKED,
    PARTIAL,
    COMPLETE,
}

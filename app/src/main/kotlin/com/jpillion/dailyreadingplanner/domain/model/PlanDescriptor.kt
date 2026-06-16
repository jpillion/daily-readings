package com.jpillion.dailyreadingplanner.domain.model

/**
 * A plan's self-declared shape (ESpec-alt §3.5, D-ALT-2/4). Parsed from the schema-v3 asset head.
 *
 * This is the stream-identity seam: every surface that needs the stream count (N) or a stream
 * title reads it from here — never a hard-coded `3` / `1095` / the `Stream` enum. In Sprint A the
 * descriptor is stood up and validated; the use cases begin consuming it in Sprint C/D.
 */
data class PlanDescriptor(
    val planId: String,
    val name: String,
    val anchoring: String,
    val dayCount: Int,
    val streams: List<StreamDescriptor>,
) {
    /** The number of parallel reading streams this plan schedules each day (1..N). */
    val streamCount: Int get() = streams.size
}

/** One within-a-day reading track's identity: its canonical [number] (1..N) and display [title]. */
data class StreamDescriptor(
    val number: Int,
    val title: String,
)

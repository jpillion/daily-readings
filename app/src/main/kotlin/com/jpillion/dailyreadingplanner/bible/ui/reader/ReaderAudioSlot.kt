package com.jpillion.dailyreadingplanner.bible.ui.reader

import androidx.compose.runtime.Composable

/**
 * VC-T8 / D-V3-14 — the reserved audio transport seam. Renders NOTHING in V3.0. It exists so V4
 * "yield-to-user autoscroll + tap-to-seek" follow-along is an additive drop-in (the [ReaderScreen]
 * already hosts it as the Scaffold `bottomBar`, and `ReaderUiState.Content.activeVerseId` already
 * carries the highlight target). No player, no network, no audio table, no INTERNET in V3.
 */
@Composable
fun ReaderAudioSlot() {
    // Intentionally empty (V4 audio gate).
}

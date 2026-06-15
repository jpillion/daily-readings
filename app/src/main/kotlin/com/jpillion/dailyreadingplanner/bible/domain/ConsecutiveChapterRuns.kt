package com.jpillion.dailyreadingplanner.bible.domain

import com.jpillion.dailyreadingplanner.domain.model.Reference

/**
 * VB-T5 / ESpec-v3 §5.4, D-V3-10 — the single home of the "group consecutive ascending chapters
 * of the same book" algorithm, lifted out of `ProviderUrlBuilder` so BOTH the external egress
 * (`ProviderUrlBuilder.bibleGatewayUrl`) and the internal nav/bridge call ONE implementation and
 * cannot drift. Lives in `bible/domain` (pure, no Android, no UI, no `data/reference` egress) so
 * the data layer and the reader can both depend on it.
 *
 * Order-preserving. A run is a maximal stretch where each ref is in the same book as and exactly
 * one chapter after its predecessor. The two-book portion (2 John 1; 3 John 1) yields two
 * singleton runs (different books) — the equivalence pin both call sites share.
 */
object ConsecutiveChapterRuns {
    fun group(refs: List<Reference>): List<List<Reference>> {
        val runs = mutableListOf<MutableList<Reference>>()
        for (ref in refs) {
            val current = runs.lastOrNull()
            val previous = current?.last()
            if (previous != null && previous.book == ref.book && previous.chapter + 1 == ref.chapter) {
                current.add(ref)
            } else {
                runs.add(mutableListOf(ref))
            }
        }
        return runs
    }
}

package com.jpillion.dailyreadingplanner.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * S-L (D-L-2) — the load-bearing in-app-update gate, pinned across every version-code boundary the
 * owner called out: patch=silent, minor=prompt, major=prompt, equal=silent, unavailable=silent.
 * Version codes follow D-S9-3: `MAJOR*10000 + MINOR*100 + PATCH`.
 *
 * Mutation targets these pins kill:
 *  - flipping `signal != UPDATE_AVAILABLE` to `==` → `signal NONE prompts` reddens.
 *  - removing the `available <= current` guard → `equal version is silent` /
 *    `an older available version is silent` redden.
 *  - flipping `available / 100 != current / 100` to `==` → `a patch-only bump is silent` AND
 *    `a minor bump prompts` redden (they are exact inverses across the boundary).
 *  - changing the divisor (e.g. /100 → /10 or /1) → `a patch-only bump is silent` reddens
 *    (1.4.1 → 1.4.2 would wrongly prompt) and `a minor bump prompts` would still need /100 to pass.
 */
class UpdatePromptDecisionTest {
    private val current = 10401 // 1.4.1

    private fun decide(
        available: Int,
        signal: UpdateAvailabilitySignal = UpdateAvailabilitySignal.UPDATE_AVAILABLE,
    ) = UpdatePromptDecision.shouldPrompt(current, available, signal)

    @Test
    fun `a patch-only bump is silent`() {
        // 1.4.1 -> 1.4.2: only the PATCH digit changed. No prompt (G2).
        assertThat(decide(10402)).isFalse()
        // larger patch jump within the same minor, still silent.
        assertThat(decide(10499)).isFalse()
    }

    @Test
    fun `a minor bump prompts`() {
        // 1.4.1 -> 1.5.0: the MINOR digit changed. Prompt.
        assertThat(decide(10500)).isTrue()
        // 1.4.1 -> 1.5.3: minor changed even though patch also moved. Prompt.
        assertThat(decide(10503)).isTrue()
    }

    @Test
    fun `a major bump prompts`() {
        // 1.4.1 -> 2.0.0: MAJOR changed (minor-or-higher digits differ). Prompt.
        assertThat(decide(20000)).isTrue()
    }

    @Test
    fun `an equal version is silent`() {
        // Same version code: nothing newer. No prompt.
        assertThat(decide(current)).isFalse()
    }

    @Test
    fun `an older available version is silent`() {
        // Defensive: a reported-available code that is somehow lower never prompts.
        assertThat(decide(10400)).isFalse()
        assertThat(decide(10301)).isFalse()
    }

    @Test
    fun `no available update is silent regardless of version codes`() {
        // signal == NONE: even a clear minor jump must not prompt (FR-NV6 — nothing offered).
        assertThat(decide(10500, signal = UpdateAvailabilitySignal.NONE)).isFalse()
        assertThat(decide(20000, signal = UpdateAvailabilitySignal.NONE)).isFalse()
    }

    @Test
    fun `the minor boundary is exact - 99-patch then next-minor`() {
        // 1.4.1 -> 1.4.99 (max patch, same minor): silent.
        assertThat(decide(10499)).isFalse()
        // 1.4.1 -> 1.5.0 (first code of the next minor): prompt. The two straddle the /100 boundary.
        assertThat(decide(10500)).isTrue()
    }
}

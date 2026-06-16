package com.jpillion.dailyreadingplanner.data.progress

import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.data.plan.PlanRegistry
import org.junit.Test

/**
 * SB-T1 (D-ALT-13) anti-drift pin: the plan id the migration stamps onto every pre-Sprint-B mark
 * IS the registry's flagship default. If these two ever diverge, an upgrading user's marks would be
 * stamped with an id the active-plan default never reads — silent invisibility. This pin makes that
 * a compile-and-test failure, not a field incident.
 */
class ReadingProgressEntityConstantTest {
    @Test
    fun `the migration stamp constant equals the registry default plan id`() {
        assertThat(ReadingProgressEntity.DEFAULT_PLAN_ID).isEqualTo(PlanRegistry.DEFAULT_PLAN_ID)
        assertThat(ReadingProgressEntity.DEFAULT_PLAN_ID).isEqualTo("bible_companion")
    }
}

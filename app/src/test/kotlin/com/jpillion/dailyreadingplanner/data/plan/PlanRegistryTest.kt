package com.jpillion.dailyreadingplanner.data.plan

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File

/** SA-T3: the bundled registry enumerates the curated set + the pinned flagship default (D-ALT-1). */
@OptIn(ExperimentalCoroutinesApi::class)
class PlanRegistryTest {
    private val assetsRoot = File(System.getProperty("planAssetsDir") ?: error("planAssetsDir not set"))

    private fun registry() =
        PlanRegistry(
            { assetPath -> assetsRoot.resolve(assetPath).readText() },
            UnconfinedTestDispatcher(),
        )

    @Test
    fun `the registry default is the bible companion flagship`() =
        runTest {
            assertThat(registry().defaultPlanId()).isEqualTo("bible_companion")
            // The build constant the migration's plan_id stamp (Sprint B) keys off must agree.
            assertThat(PlanRegistry.DEFAULT_PLAN_ID).isEqualTo("bible_companion")
        }

    @Test
    fun `the registry enumerates bible_companion, mcheyne and chronological with their asset paths`() =
        runTest {
            val plans = registry().plans()
            assertThat(plans.map { it.id })
                .containsExactly("bible_companion", "mcheyne", "chronological")
                .inOrder()
            assertThat(plans.first { it.id == "bible_companion" }.asset)
                .isEqualTo("plans/bible_companion/plan.json")
            assertThat(plans.first { it.id == "mcheyne" }.asset).isEqualTo("plans/mcheyne/plan.json")
            assertThat(plans.first { it.id == "chronological" }.asset)
                .isEqualTo("plans/chronological/plan.json")
        }

    @Test
    fun `assetFor resolves a known id and null-degrades an unknown one`() =
        runTest {
            val r = registry()
            assertThat(r.assetFor("bible_companion")).isEqualTo("plans/bible_companion/plan.json")
            assertThat(r.assetFor("not_a_plan")).isNull()
            assertThat(r.isKnown("mcheyne")).isTrue()
            assertThat(r.isKnown("not_a_plan")).isFalse()
        }
}

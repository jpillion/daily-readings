package com.jpillion.dailyreadingplanner.data.plan

import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.testing.FakeSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File

/** SA-T5: the active plan defaults to the flagship and degrades unknown stored ids (D-ALT-16/17). */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivePlanRepositoryTest {
    private val assetsRoot = File(System.getProperty("planAssetsDir") ?: error("planAssetsDir not set"))

    private fun source() = PlanAssetSource { assetPath -> assetsRoot.resolve(assetPath).readText() }

    private fun newActiveRepo(settings: FakeSettingsRepository): ActivePlanRepositoryImpl {
        val dispatcher = UnconfinedTestDispatcher()
        val registry = PlanRegistry(source(), dispatcher)
        val planRepo = ReadingPlanRepositoryImpl(registry, ReadingPlanAssetLoader(source(), dispatcher))
        return ActivePlanRepositoryImpl(settings, registry, planRepo)
    }

    @Test
    fun `the default active plan is the bible companion`() =
        runTest {
            val settings = FakeSettingsRepository().apply { storedSelectedPlanId.value = "bible_companion" }
            val repo = newActiveRepo(settings)
            assertThat(repo.activePlanId.first()).isEqualTo("bible_companion")
            assertThat(repo.activeDescriptor.first().planId).isEqualTo("bible_companion")
            assertThat(repo.activeDescriptor.first().streamCount).isEqualTo(3)
        }

    @Test
    fun `a known stored id is the active plan`() =
        runTest {
            val settings = FakeSettingsRepository().apply { storedSelectedPlanId.value = "mcheyne" }
            val repo = newActiveRepo(settings)
            assertThat(repo.activePlanId.first()).isEqualTo("mcheyne")
            assertThat(repo.activeDescriptor.first().streamCount).isEqualTo(4)
        }

    @Test
    fun `an unknown stored id degrades to the flagship default`() =
        runTest {
            val settings = FakeSettingsRepository().apply { storedSelectedPlanId.value = "deleted_plan" }
            val repo = newActiveRepo(settings)
            assertThat(repo.activePlanId.first()).isEqualTo("bible_companion")
            assertThat(repo.activeDescriptor.first().planId).isEqualTo("bible_companion")
        }
}

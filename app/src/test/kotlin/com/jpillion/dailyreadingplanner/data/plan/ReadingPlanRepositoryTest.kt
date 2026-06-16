package com.jpillion.dailyreadingplanner.data.plan

import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.core.date.ReadingDate
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import com.jpillion.dailyreadingplanner.domain.model.Reference
import com.jpillion.dailyreadingplanner.domain.model.Stream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * SA-T4: the per-plan repository resolves a planId to its asset via the registry, parses the REAL
 * bundled asset (same planAssetsDir mechanism as the gate) into domain models, and memoizes each
 * plan single-flight. The Bible-Companion path produces the identical map it did before the move.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReadingPlanRepositoryTest {
    private val assetsRoot = File(System.getProperty("planAssetsDir") ?: error("planAssetsDir not set"))

    private val loadCount = AtomicInteger(0)

    /** Resolves asset paths relative to the plans/ dir; counts every distinct asset read. */
    private val countingSource =
        PlanAssetSource { assetPath ->
            loadCount.incrementAndGet()
            // assetPath is "plans/<id>/plan.json" or "plans/registry.json" — strip the leading "plans/".
            assetsRoot.resolve(assetPath).readText()
        }

    private fun newRepo(): ReadingPlanRepositoryImpl {
        val dispatcher = UnconfinedTestDispatcher()
        val registry = PlanRegistry(countingSource, dispatcher)
        val loader = ReadingPlanAssetLoader(countingSource, dispatcher)
        return ReadingPlanRepositoryImpl(registry, loader)
    }

    private fun ref(
        book: String,
        chapter: Int,
    ) = Reference(BookCatalog.requireByName(book), chapter)

    @Test
    fun `bible_companion jan 1 anchor resolves to genesis - psalms - matthew`() =
        runTest {
            val repo = newRepo()
            val portions = repo.portionsFor("bible_companion", ReadingDate(1, 1))
            assertThat(portions.map { it.stream })
                .containsExactly(Stream.LAW_AND_HISTORY, Stream.PSALMS_AND_PROPHECY, Stream.NEW_TESTAMENT)
                .inOrder()
            assertThat(portions[0].refs).containsExactly(ref("Genesis", 1), ref("Genesis", 2)).inOrder()
            assertThat(portions[1].refs).containsExactly(ref("Psalms", 1), ref("Psalms", 2)).inOrder()
            assertThat(portions[2].refs).containsExactly(ref("Matthew", 1), ref("Matthew", 2)).inOrder()
        }

    @Test
    fun `a portion can span two books - jun 19 and dec 19 NT portion is 2 john plus 3 john`() =
        runTest {
            val repo = newRepo()
            for (date in listOf(ReadingDate(6, 19), ReadingDate(12, 19))) {
                val nt = repo.portionsFor("bible_companion", date).first { it.stream == Stream.NEW_TESTAMENT }
                assertThat(nt.refs).containsExactly(ref("2 John", 1), ref("3 John", 1)).inOrder()
                assertThat(nt.firstRef).isEqualTo(ref("2 John", 1))
            }
        }

    @Test
    fun `every valid reading date resolves to exactly 3 portions`() =
        runTest {
            val repo = newRepo()
            val daysPerMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
            var checked = 0
            for (month in 1..12) {
                for (day in 1..daysPerMonth[month - 1]) {
                    assertThat(repo.portionsFor("bible_companion", ReadingDate(month, day))).hasSize(3)
                    checked++
                }
            }
            assertThat(checked).isEqualTo(365)
        }

    @Test
    fun `the bible_companion descriptor declares 3 streams with their titles`() =
        runTest {
            val descriptor = newRepo().descriptor("bible_companion")
            assertThat(descriptor.planId).isEqualTo("bible_companion")
            assertThat(descriptor.name).isEqualTo("Bible Companion")
            assertThat(descriptor.anchoring).isEqualTo("DATE")
            assertThat(descriptor.dayCount).isEqualTo(365)
            assertThat(descriptor.streams.map { it.title })
                .containsExactly("Law & History", "Psalms & Prophecy", "New Testament")
                .inOrder()
        }

    @Test
    fun `the active plan's asset is read once across repeated and concurrent lookups`() =
        runTest {
            val repo = newRepo()
            // first touch loads registry (1) + bible_companion asset; load+descriptor read the
            // same asset path twice on the first miss. The point: repeated lookups don't re-read.
            repo.portionsFor("bible_companion", ReadingDate(1, 1))
            val readsAfterFirst = loadCount.get()
            val lookups = (1..20).map { async { repo.portionsFor("bible_companion", ReadingDate(6, 19)) } }
            lookups.forEach { it.await() }
            repo.portionsFor("bible_companion", ReadingDate(12, 25))
            assertThat(loadCount.get()).isEqualTo(readsAfterFirst)
        }

    @Test
    fun `each plan caches independently - touching mcheyne does not re-read bible_companion`() =
        runTest {
            val repo = newRepo()
            repo.portionsFor("bible_companion", ReadingDate(1, 1))
            val afterBc = loadCount.get()
            // Touch the OTHER plan via descriptor() (mcheyne is 4-stream; the domain Portion map
            // via the Stream enum is exercised only on <=3-stream plans this sprint — Stream retires
            // in Sprint C). The point: mcheyne's asset is read, bible_companion is not re-read.
            repo.descriptor("mcheyne")
            assertThat(loadCount.get()).isGreaterThan(afterBc)
            // And re-touching bible_companion is still a cache hit (no further reads).
            val afterMcheyne = loadCount.get()
            repo.portionsFor("bible_companion", ReadingDate(2, 2))
            assertThat(loadCount.get()).isEqualTo(afterMcheyne)
        }
}

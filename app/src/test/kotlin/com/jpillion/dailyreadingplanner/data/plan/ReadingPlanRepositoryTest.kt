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
 * S3-T4: the loader parses the REAL bundled asset (same planAssetsDir mechanism as the
 * release gate) into domain models, and the repository memoizes it single-flight.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReadingPlanRepositoryTest {
    private val planText: String =
        File(System.getProperty("planAssetsDir") ?: error("planAssetsDir not set"))
            .resolve("reading_plan.json")
            .readText()

    private val loadCount = AtomicInteger(0)
    private val countingSource =
        PlanJsonSource {
            loadCount.incrementAndGet()
            planText
        }

    private fun ref(
        book: String,
        chapter: Int,
    ) = Reference(BookCatalog.requireByName(book), chapter)

    @Test
    fun `jan 1 anchor resolves to genesis - psalms - matthew`() =
        runTest {
            val repo =
                ReadingPlanRepositoryImpl(
                    ReadingPlanAssetLoader(countingSource, UnconfinedTestDispatcher(testScheduler)),
                )
            val portions = repo.portionsFor(ReadingDate(1, 1))
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
            val repo =
                ReadingPlanRepositoryImpl(
                    ReadingPlanAssetLoader(countingSource, UnconfinedTestDispatcher(testScheduler)),
                )
            for (date in listOf(ReadingDate(6, 19), ReadingDate(12, 19))) {
                val nt = repo.portionsFor(date).first { it.stream == Stream.NEW_TESTAMENT }
                assertThat(nt.refs).containsExactly(ref("2 John", 1), ref("3 John", 1)).inOrder()
                assertThat(nt.firstRef).isEqualTo(ref("2 John", 1))
            }
        }

    @Test
    fun `every valid reading date resolves to exactly 3 portions`() =
        runTest {
            val repo =
                ReadingPlanRepositoryImpl(
                    ReadingPlanAssetLoader(countingSource, UnconfinedTestDispatcher(testScheduler)),
                )
            val daysPerMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
            var checked = 0
            for (month in 1..12) {
                for (day in 1..daysPerMonth[month - 1]) {
                    assertThat(repo.portionsFor(ReadingDate(month, day))).hasSize(3)
                    checked++
                }
            }
            assertThat(checked).isEqualTo(365)
        }

    @Test
    fun `the asset is read exactly once across repeated and concurrent lookups`() =
        runTest {
            val repo =
                ReadingPlanRepositoryImpl(
                    ReadingPlanAssetLoader(countingSource, UnconfinedTestDispatcher(testScheduler)),
                )
            val lookups = (1..20).map { async { repo.portionsFor(ReadingDate(6, 19)) } }
            lookups.forEach { it.await() }
            repo.portionsFor(ReadingDate(12, 25))
            assertThat(loadCount.get()).isEqualTo(1)
        }
}

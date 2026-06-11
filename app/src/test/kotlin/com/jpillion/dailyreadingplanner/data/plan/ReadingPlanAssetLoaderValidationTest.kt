package com.jpillion.dailyreadingplanner.data.plan

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** S3-T4: the loader fails fast on structurally invalid plan data (ESpec §5.1 validation). */
@OptIn(ExperimentalCoroutinesApi::class)
class ReadingPlanAssetLoaderValidationTest {
    private fun day(
        month: Int,
        day: Int,
        streams: List<Int> = listOf(1, 2, 3),
        book: String = "Genesis",
    ): String {
        val portions =
            streams.joinToString(",") { s ->
                """{"stream":$s,"refs":[{"book":"$book","chapter":1}]}"""
            }
        return """{"month":$month,"day":$day,"portions":[$portions]}"""
    }

    private fun plan(
        days: String,
        schemaVersion: Int = 1,
    ) = """{"schemaVersion":$schemaVersion,"source":"test","days":[$days]}"""

    private fun fullYear(mutate: (MutableList<String>) -> Unit = {}): String {
        val daysPerMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        val days = mutableListOf<String>()
        for (m in 1..12) for (d in 1..daysPerMonth[m - 1]) days += day(m, d)
        mutate(days)
        return plan(days.joinToString(","))
    }

    private fun loadResult(json: String) =
        runCatching {
            runTest {
                ReadingPlanAssetLoader({ json }, UnconfinedTestDispatcher(testScheduler)).load()
            }
        }

    @Test
    fun `a structurally valid 365-day plan loads`() {
        assertThat(loadResult(fullYear()).isSuccess).isTrue()
    }

    @Test
    fun `rejects an unsupported schema version`() {
        val json = fullYear().replace(""""schemaVersion":1""", """"schemaVersion":2""")
        assertThat(loadResult(json).exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `rejects a plan that is not exactly 365 days`() {
        val json = fullYear { it.removeAt(0) }
        assertThat(loadResult(json).exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `rejects duplicate days`() {
        val json = fullYear { it[1] = it[0] }
        assertThat(loadResult(json).exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `rejects a day whose streams are not exactly 1-2-3`() {
        val json = fullYear { it[0] = day(1, 1, streams = listOf(1, 2, 2)) }
        assertThat(loadResult(json).exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `rejects a book that is not in the catalog`() {
        val json = fullYear { it[0] = day(1, 1, book = "Genesys") }
        assertThat(loadResult(json).exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }
}

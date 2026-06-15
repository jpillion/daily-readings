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
        schemaVersion: Int = 2,
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
        val json = fullYear().replace(""""schemaVersion":2""", """"schemaVersion":3""")
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

    /** A whole-chapter (no verse fields) plan still loads under schema v2 — backward-identical. */
    @Test
    fun `a windowed ref with both bounds present loads`() {
        val json =
            fullYear {
                it[0] =
                    """{"month":1,"day":1,"portions":[""" +
                    """{"stream":1,"refs":[{"book":"Psalms","chapter":119,"verseStart":1,"verseEnd":40}]},""" +
                    """{"stream":2,"refs":[{"book":"Genesis","chapter":1}]},""" +
                    """{"stream":3,"refs":[{"book":"Genesis","chapter":1}]}]}"""
            }
        assertThat(loadResult(json).isSuccess).isTrue()
    }

    /** A lone verse bound (start without end) is ambiguous and rejected at load (defense-in-depth). */
    @Test
    fun `rejects a ref with only verseStart and no verseEnd`() {
        val json =
            fullYear {
                it[0] =
                    """{"month":1,"day":1,"portions":[""" +
                    """{"stream":1,"refs":[{"book":"Psalms","chapter":119,"verseStart":1}]},""" +
                    """{"stream":2,"refs":[{"book":"Genesis","chapter":1}]},""" +
                    """{"stream":3,"refs":[{"book":"Genesis","chapter":1}]}]}"""
            }
        assertThat(loadResult(json).exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
    }

    /** A reversed verse window (start > end) is rejected (ReferenceVerses init require). */
    @Test
    fun `rejects a ref whose verseStart is after verseEnd`() {
        val json =
            fullYear {
                it[0] =
                    """{"month":1,"day":1,"portions":[""" +
                    """{"stream":1,"refs":[{"book":"Psalms","chapter":119,"verseStart":40,"verseEnd":1}]},""" +
                    """{"stream":2,"refs":[{"book":"Genesis","chapter":1}]},""" +
                    """{"stream":3,"refs":[{"book":"Genesis","chapter":1}]}]}"""
            }
        assertThat(loadResult(json).exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }
}

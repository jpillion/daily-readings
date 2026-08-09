package com.jpillion.dailyreadingplanner.data.plan

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * SA-T2: the descriptor-driven loader fails fast on structurally invalid plan data (ESpec-alt
 * §3.5). Validation now reads `dayCount`/`streams[]` from the schema-v3 head, not the old
 * `365`/`listOf(1,2,3)` constants — but the *intent* (reject bad shapes) carries over from S3.
 */
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

    /** A schema-v3 head + the given day body. Defaults: bible_companion, DATE, 365, 3 streams. */
    private fun plan(
        days: String,
        schemaVersion: Int = 3,
        planId: String = "bible_companion",
        anchoring: String = "DATE",
        dayCount: Int = 365,
        streams: List<Int> = listOf(1, 2, 3),
    ): String {
        val streamsJson = streams.joinToString(",") { """{"number":$it,"title":"Stream $it"}""" }
        return """{"schemaVersion":$schemaVersion,"planId":"$planId","name":"Test",""" +
            """"source":"test","anchoring":"$anchoring","dayCount":$dayCount,""" +
            """"streams":[$streamsJson],"days":[$days]}"""
    }

    private fun fullYear(
        streams: List<Int> = listOf(1, 2, 3),
        mutate: (MutableList<String>) -> Unit = {},
    ): String {
        val daysPerMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        val days = mutableListOf<String>()
        for (m in 1..12) for (d in 1..daysPerMonth[m - 1]) days += day(m, d, streams = streams)
        mutate(days)
        return plan(days.joinToString(","), streams = streams)
    }

    private fun loadResult(
        json: String,
        expectedPlanId: String = "bible_companion",
    ) = runCatching {
        runTest {
            ReadingPlanAssetLoader({ _ -> json }, UnconfinedTestDispatcher(testScheduler))
                .load("plans/x/plan.json", expectedPlanId)
        }
    }

    @Test
    fun `a structurally valid 365-day v3 plan loads`() {
        assertThat(loadResult(fullYear()).isSuccess).isTrue()
    }

    @Test
    fun `rejects an unsupported schema version`() {
        val json = fullYear().replace(""""schemaVersion":3""", """"schemaVersion":2""")
        assertThat(loadResult(json).exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `rejects a plan whose declared planId differs from the expected id - anti-drift`() {
        assertThat(loadResult(fullYear(), expectedPlanId = "mcheyne").exceptionOrNull())
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `rejects a non-DATE anchoring`() {
        val json = fullYear().replace(""""anchoring":"DATE"""", """"anchoring":"PROGRESS"""")
        assertThat(loadResult(json).exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `rejects a dayCount other than 365`() {
        val json = fullYear().replace(""""dayCount":365""", """"dayCount":366""")
        assertThat(loadResult(json).exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `rejects a plan whose day count does not match dayCount`() {
        val json = fullYear { it.removeAt(0) }
        assertThat(loadResult(json).exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `rejects duplicate days`() {
        val json = fullYear { it[1] = it[0] }
        assertThat(loadResult(json).exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `rejects a Feb 29 entry`() {
        // append a 29th Feb day -> 366 days; the dayCount-match check and the Feb-29 check both fire.
        val json =
            fullYear { it.add(day(2, 29)) }
                .replace(""""dayCount":365""", """"dayCount":366""")
        assertThat(loadResult(json).exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `rejects a day whose stream set is not exactly the declared streams`() {
        val json = fullYear { it[0] = day(1, 1, streams = listOf(1, 2, 2)) }
        assertThat(loadResult(json).exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `rejects non-contiguous declared stream numbers`() {
        // streams declared 1,2,4 (gap) — not 1..N contiguous.
        val json = fullYear(streams = listOf(1, 2, 4))
        assertThat(loadResult(json).exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `rejects a book that is not in the catalog`() {
        val json = fullYear { it[0] = day(1, 1, book = "Genesys") }
        assertThat(loadResult(json).exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }

    /**
     * SA-T2 generalization proof: a plan declaring a DIFFERENT stream count (N=4, the M'Cheyne
     * shape) passes the descriptor-driven validation — the loader is N-agnostic at the DTO level.
     * (The domain `Portion` mapping via the `Stream` enum is exercised only on N<=3 plans this
     * sprint; the `Stream` enum is retired in Sprint C — so this asserts the descriptor only.)
     */
    @Test
    fun `a synthetic 4-stream plan passes descriptor validation`() {
        val json = fullYear(streams = listOf(1, 2, 3, 4))
        runTest {
            val descriptor =
                ReadingPlanAssetLoader({ _ -> json }, UnconfinedTestDispatcher(testScheduler))
                    .descriptor("plans/x/plan.json", "bible_companion")
            assertThat(descriptor.streamCount).isEqualTo(4)
            assertThat(descriptor.streams.map { it.number }).isEqualTo(listOf(1, 2, 3, 4))
        }
    }

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

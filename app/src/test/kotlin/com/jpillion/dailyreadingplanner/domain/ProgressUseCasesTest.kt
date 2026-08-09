package com.jpillion.dailyreadingplanner.domain

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Test

/** S3-T7: ToggleReading and MarkWholeDay write through to the progress store. */
class ProgressUseCasesTest {
    private val progress = FakeProgressRepository()
    private val date = LocalDate(2026, 6, 10)

    @Test
    fun `toggle reading marks and unmarks a single stream`() =
        runTest {
            val toggle = ToggleReadingUseCase(progress, FakeActivePlanRepository())
            toggle(date, 2, markRead = true)
            assertThat(progress.marksFor(date)).containsExactlyInAnyOrder(2)
            toggle(date, 2, markRead = false)
            assertThat(progress.marksFor(date)).isEmpty()
        }

    @Test
    fun `mark whole day marks and unmarks all three streams`() =
        runTest {
            val markWholeDay = MarkWholeDayUseCase(progress, FakeActivePlanRepository())
            markWholeDay(date, markRead = true)
            assertThat(progress.marksFor(date)).containsExactlyInAnyOrder(1, 2, 3)
            markWholeDay(date, markRead = false)
            assertThat(progress.marksFor(date)).isEmpty()
        }
}

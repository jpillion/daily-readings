package com.jpillion.dailyreadingplanner.domain

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

/** S3-T7: ToggleReading and MarkWholeDay write through to the progress store. */
class ProgressUseCasesTest {
    private val progress = FakeProgressRepository()
    private val date = LocalDate.of(2026, 6, 10)

    @Test
    fun `toggle reading marks and unmarks a single stream`() =
        runTest {
            val toggle = ToggleReadingUseCase(progress, FakeActivePlanRepository())
            toggle(date, 2, markRead = true)
            assertThat(progress.marksFor(date)).containsExactly(2)
            toggle(date, 2, markRead = false)
            assertThat(progress.marksFor(date)).isEmpty()
        }

    @Test
    fun `mark whole day marks and unmarks all three streams`() =
        runTest {
            val markWholeDay = MarkWholeDayUseCase(progress, FakeActivePlanRepository())
            markWholeDay(date, markRead = true)
            assertThat(progress.marksFor(date)).containsExactly(1, 2, 3)
            markWholeDay(date, markRead = false)
            assertThat(progress.marksFor(date)).isEmpty()
        }
}

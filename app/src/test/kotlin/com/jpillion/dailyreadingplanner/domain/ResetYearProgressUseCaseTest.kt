package com.jpillion.dailyreadingplanner.domain

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import com.jpillion.dailyreadingplanner.platform.FakeDateProvider
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.time.Instant

/** S8: "Reset progress" clears exactly the dateProvider's current year (owner decision). */
class ResetYearProgressUseCaseTest {
    private val progress = FakeProgressRepository()

    @Test
    fun `clears the current year per the injected dateProvider`() =
        runTest {
            val dateProvider =
                FakeDateProvider(LocalDate(2026, 6, 10), now = Instant.parse("2026-06-10T12:00:00Z"))
            ResetYearProgressUseCase(progress, dateProvider).invoke()
            assertThat(progress.clearYearCalls).containsExactlyInAnyOrder(2026)
        }

    @Test
    fun `the year tracks the dateProvider - not a constant`() =
        runTest {
            val dateProvider = FakeDateProvider(LocalDate(2031, 1, 1))
            ResetYearProgressUseCase(progress, dateProvider).invoke()
            assertThat(progress.clearYearCalls).containsExactlyInAnyOrder(2031)
        }
}

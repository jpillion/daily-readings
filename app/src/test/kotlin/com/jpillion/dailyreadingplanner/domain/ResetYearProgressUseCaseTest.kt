package com.jpillion.dailyreadingplanner.domain

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** S8: "Reset progress" clears exactly the clock's current year (owner decision). */
class ResetYearProgressUseCaseTest {
    private val progress = FakeProgressRepository()

    @Test
    fun `clears the current year per the injected clock`() =
        runTest {
            val clock = Clock.fixed(Instant.parse("2026-06-10T12:00:00Z"), ZoneOffset.UTC)
            ResetYearProgressUseCase(progress, clock).invoke()
            assertThat(progress.clearYearCalls).containsExactly(2026)
        }

    @Test
    fun `the year tracks the clock - not a constant`() =
        runTest {
            val clock = Clock.fixed(Instant.parse("2031-01-01T00:00:00Z"), ZoneOffset.UTC)
            ResetYearProgressUseCase(progress, clock).invoke()
            assertThat(progress.clearYearCalls).containsExactly(2031)
        }
}

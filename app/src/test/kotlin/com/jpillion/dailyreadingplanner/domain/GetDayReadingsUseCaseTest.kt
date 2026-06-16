package com.jpillion.dailyreadingplanner.domain

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.core.date.ScheduleDateResolver
import com.jpillion.dailyreadingplanner.domain.model.DayReadings
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

/** S3-T7: schedule + progress combine into DayReadings; Feb 29 yields the no-readings state. */
class GetDayReadingsUseCaseTest {
    private val progress = FakeProgressRepository()
    private val useCase =
        GetDayReadingsUseCase(
            resolver = ScheduleDateResolver(),
            planRepository = FakeReadingPlanRepository(),
            progressRepository = progress,
            activePlanRepository = FakeActivePlanRepository(),
        )

    @Test
    fun `a scheduled day with no marks yields three unread readings`() =
        runTest {
            useCase(LocalDate.of(2026, 1, 1)).test {
                val scheduled = awaitItem() as DayReadings.Scheduled
                assertThat(scheduled.readings).hasSize(3)
                assertThat(scheduled.readings.map { it.isRead }).containsExactly(false, false, false)
                assertThat(scheduled.dayComplete).isFalse()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `read marks flow through and day completes when all three are read`() =
        runTest {
            val date = LocalDate.of(2026, 1, 1)
            useCase(date).test {
                assertThat((awaitItem() as DayReadings.Scheduled).dayComplete).isFalse()

                progress.setRead(date, 1, isRead = true)
                val partial = awaitItem() as DayReadings.Scheduled
                assertThat(partial.readings.single { it.portion.streamNumber == 1 }.isRead).isTrue()
                assertThat(partial.dayComplete).isFalse()

                progress.setWholeDay(date, listOf(1, 2, 3), isRead = true)
                val complete = awaitItem() as DayReadings.Scheduled
                assertThat(complete.readings.map { it.isRead }).containsExactly(true, true, true)
                assertThat(complete.dayComplete).isTrue()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `marks on one date do not leak into another year's same calendar day`() =
        runTest {
            progress.setWholeDay(LocalDate.of(2026, 1, 1), listOf(1, 2, 3), isRead = true)
            useCase(LocalDate.of(2027, 1, 1)).test {
                val day = awaitItem() as DayReadings.Scheduled
                assertThat(day.readings.map { it.isRead }).containsExactly(false, false, false)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `feb 29 yields the explicit no-readings state and never touches progress`() =
        runTest {
            useCase(LocalDate.of(2028, 2, 29)).test {
                assertThat(awaitItem()).isEqualTo(DayReadings.NoScheduledReadings(LocalDate.of(2028, 2, 29)))
                awaitComplete()
            }
            assertThat(progress.streamsReadQueries).isEqualTo(0)
        }
}

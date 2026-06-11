package com.jpillion.dailyreadingplanner.domain

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.core.date.ScheduleDateResolver
import com.jpillion.dailyreadingplanner.domain.model.DayCompletion
import com.jpillion.dailyreadingplanner.domain.model.Stream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * S8-T2: the classification behind the date-picker indicators (owner spec). Green/COMPLETE for
 * any fully-read day; red/MISSED only for *past* scheduled days short of three marks; NONE for
 * incomplete today/future days; Feb 29 is always NONE (D1) — never "missed".
 */
class GetMonthCompletionUseCaseTest {
    // Fixed "today": 2026-06-10.
    private val clock = Clock.fixed(Instant.parse("2026-06-10T12:00:00Z"), ZoneOffset.UTC)
    private val progress = FakeProgressRepository()
    private val useCase = GetMonthCompletionUseCase(ScheduleDateResolver(), progress, clock)

    private fun Map<LocalDate, DayCompletion>.june(day: Int) = this[LocalDate.of(2026, 6, day)]

    @Test
    fun `classifies complete, missed and none across past, today and future`() =
        runTest {
            progress.setWholeDay(LocalDate.of(2026, 6, 1), isRead = true) // past, complete
            progress.setRead(LocalDate.of(2026, 6, 2), Stream.NEW_TESTAMENT, isRead = true) // past, partial
            // 2026-06-03: past, zero marks
            progress.setWholeDay(LocalDate.of(2026, 6, 10), isRead = true) // today, complete
            progress.setWholeDay(LocalDate.of(2026, 6, 20), isRead = true) // future, complete

            val map = useCase(YearMonth.of(2026, 6)).first()
            assertThat(map.june(1)).isEqualTo(DayCompletion.COMPLETE)
            assertThat(map.june(2)).isEqualTo(DayCompletion.MISSED)
            assertThat(map.june(3)).isEqualTo(DayCompletion.MISSED)
            assertThat(map.june(9)).isEqualTo(DayCompletion.MISSED)
            assertThat(map.june(10)).isEqualTo(DayCompletion.COMPLETE) // today, all three
            assertThat(map.june(11)).isEqualTo(DayCompletion.NONE) // future, incomplete
            assertThat(map.june(20)).isEqualTo(DayCompletion.COMPLETE) // future, complete
        }

    @Test
    fun `today incomplete is NONE, not missed`() =
        runTest {
            progress.setRead(LocalDate.of(2026, 6, 10), Stream.LAW_AND_HISTORY, isRead = true)
            val map = useCase(YearMonth.of(2026, 6)).first()
            assertThat(map.june(10)).isEqualTo(DayCompletion.NONE)
        }

    @Test
    fun `two of three marks on a past day is still missed`() =
        runTest {
            progress.setRead(LocalDate.of(2026, 6, 5), Stream.LAW_AND_HISTORY, isRead = true)
            progress.setRead(LocalDate.of(2026, 6, 5), Stream.NEW_TESTAMENT, isRead = true)
            val map = useCase(YearMonth.of(2026, 6)).first()
            assertThat(map.june(5)).isEqualTo(DayCompletion.MISSED)
        }

    @Test
    fun `covers every day of the month exactly once`() =
        runTest {
            val map = useCase(YearMonth.of(2026, 6)).first()
            assertThat(map.keys).hasSize(30)
            assertThat(map.keys.minOrNull()).isEqualTo(LocalDate.of(2026, 6, 1))
            assertThat(map.keys.maxOrNull()).isEqualTo(LocalDate.of(2026, 6, 30))
        }

    @Test
    fun `a past Feb 29 is NONE, never missed`() =
        runTest {
            // Clock in March 2028: Feb 29 2028 is in the past with zero marks — but it had no
            // scheduled readings (D1), so it must not be flagged as missed.
            val marchClock = Clock.fixed(Instant.parse("2028-03-15T12:00:00Z"), ZoneOffset.UTC)
            val inMarch = GetMonthCompletionUseCase(ScheduleDateResolver(), progress, marchClock)
            val map = inMarch(YearMonth.of(2028, 2)).first()
            assertThat(map[LocalDate.of(2028, 2, 29)]).isEqualTo(DayCompletion.NONE)
            assertThat(map[LocalDate.of(2028, 2, 28)]).isEqualTo(DayCompletion.MISSED)
        }

    @Test
    fun `re-emits when marks change while collected`() =
        runTest {
            useCase(YearMonth.of(2026, 6)).test {
                assertThat(awaitItem()[LocalDate.of(2026, 6, 1)]).isEqualTo(DayCompletion.MISSED)
                progress.setWholeDay(LocalDate.of(2026, 6, 1), isRead = true)
                assertThat(awaitItem()[LocalDate.of(2026, 6, 1)]).isEqualTo(DayCompletion.COMPLETE)
            }
        }
}

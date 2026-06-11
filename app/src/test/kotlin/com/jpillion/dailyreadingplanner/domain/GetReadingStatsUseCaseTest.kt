package com.jpillion.dailyreadingplanner.domain

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.core.date.ScheduleDateResolver
import com.jpillion.dailyreadingplanner.domain.model.Stream
import com.jpillion.dailyreadingplanner.testing.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * S11: the streak walk + year/stream totals (PRD §13.1, R-STREAK-1…6). Default fixed
 * "today" = 2026-06-10; leap-year and year-boundary cases pin their own clocks.
 */
class GetReadingStatsUseCaseTest {
    private val clock = Clock.fixed(Instant.parse("2026-06-10T12:00:00Z"), ZoneOffset.UTC)
    private val progress = FakeProgressRepository()
    private val settings = FakeSettingsRepository()

    private fun useCase(at: Clock = clock) =
        GetReadingStatsUseCase(
            DayCompletionClassifier(ScheduleDateResolver()),
            progress,
            settings,
            at,
        )

    private fun june(day: Int): LocalDate = LocalDate.of(2026, 6, day)

    private suspend fun markDays(vararg dates: LocalDate) {
        dates.forEach { progress.setWholeDay(it, isRead = true) }
    }

    // --- Streaks: R-STREAK-1 (complete days only) ---

    @Test
    fun `no marks at all - both streaks are zero and totals empty`() =
        runTest {
            val stats = useCase()().first()
            assertThat(stats.currentStreakDays).isEqualTo(0)
            assertThat(stats.longestStreakDays).isEqualTo(0)
            assertThat(stats.yearReadCount).isEqualTo(0)
            assertThat(stats.streamReadCounts)
                .containsExactly(
                    Stream.LAW_AND_HISTORY,
                    0,
                    Stream.PSALMS_AND_PROPHECY,
                    0,
                    Stream.NEW_TESTAMENT,
                    0,
                )
        }

    @Test
    fun `consecutive complete days ending in a complete today extend the current streak`() =
        runTest {
            markDays(june(8), june(9), june(10))
            val stats = useCase()().first()
            assertThat(stats.currentStreakDays).isEqualTo(3)
            assertThat(stats.longestStreakDays).isEqualTo(3)
        }

    @Test
    fun `a passed day with zero marks ends the streak - longest remembers the older run`() =
        runTest {
            markDays(june(1), june(2), june(3), june(4), june(5)) // older run of 5
            // June 6: passed, no marks - breaks.
            markDays(june(7), june(8), june(9)) // current run; today (10th) unmarked = grace
            val stats = useCase()().first()
            assertThat(stats.currentStreakDays).isEqualTo(3)
            assertThat(stats.longestStreakDays).isEqualTo(5)
        }

    @Test
    fun `a passed day with two of three marks ends the streak`() =
        runTest {
            markDays(june(7))
            progress.setRead(june(8), Stream.LAW_AND_HISTORY, isRead = true)
            progress.setRead(june(8), Stream.NEW_TESTAMENT, isRead = true) // 2 of 3: not a streak day
            markDays(june(9))
            val stats = useCase()().first()
            assertThat(stats.currentStreakDays).isEqualTo(1)
            assertThat(stats.longestStreakDays).isEqualTo(1)
        }

    // --- R-STREAK-3: today is in grace ---

    @Test
    fun `an incomplete today does not break the current streak`() =
        runTest {
            markDays(june(8), june(9)) // today (10th) has zero marks
            val stats = useCase()().first()
            assertThat(stats.currentStreakDays).isEqualTo(2)
        }

    @Test
    fun `a partially complete today neither extends nor breaks`() =
        runTest {
            markDays(june(8), june(9))
            progress.setRead(june(10), Stream.NEW_TESTAMENT, isRead = true)
            val stats = useCase()().first()
            assertThat(stats.currentStreakDays).isEqualTo(2)
        }

    // --- R-STREAK-2: Feb 29 is neutral ---

    @Test
    fun `streak walks across Feb 29 - complete Feb 28 and Mar 1 are consecutive`() =
        runTest {
            val leapClock = Clock.fixed(Instant.parse("2028-03-01T12:00:00Z"), ZoneOffset.UTC)
            markDays(LocalDate.of(2028, 2, 28), LocalDate.of(2028, 3, 1))
            val stats = useCase(at = leapClock)().first()
            assertThat(stats.currentStreakDays).isEqualTo(2)
            assertThat(stats.longestStreakDays).isEqualTo(2)
        }

    // --- R-STREAK-4: streaks cross year boundaries; longest is all-time ---

    @Test
    fun `streak crosses the year boundary - Dec 30-31 plus Jan 1-2 is one run of four`() =
        runTest {
            val janClock = Clock.fixed(Instant.parse("2026-01-02T12:00:00Z"), ZoneOffset.UTC)
            markDays(
                LocalDate.of(2025, 12, 30),
                LocalDate.of(2025, 12, 31),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 2),
            )
            val stats = useCase(at = janClock)().first()
            assertThat(stats.currentStreakDays).isEqualTo(4)
            assertThat(stats.longestStreakDays).isEqualTo(4)
        }

    @Test
    fun `longest streak is all-time even when the current streak is in a later year`() =
        runTest {
            // Seven complete days in 2025; long gap; two ending at today in 2026.
            (1..7).forEach { markDays(LocalDate.of(2025, 3, it)) }
            markDays(june(9), june(10))
            val stats = useCase()().first()
            assertThat(stats.currentStreakDays).isEqualTo(2)
            assertThat(stats.longestStreakDays).isEqualTo(7)
        }

    // --- R-STREAK-5: tracking start date honored via the shared classifier ---

    @Test
    fun `pre-start incomplete days are neutral - they do not break a streak`() =
        runTest {
            // A complete day BEFORE the start keeps its earned credit (COMPLETE wins),
            // the unmarked pre-start gap is skipped, and the post-start run continues it.
            markDays(LocalDate.of(2026, 5, 1))
            settings.setTrackingStartDate(june(1))
            (1..10).forEach { markDays(june(it)) }
            val stats = useCase()().first()
            assertThat(stats.currentStreakDays).isEqualTo(11)
            assertThat(stats.longestStreakDays).isEqualTo(11)
        }

    @Test
    fun `post-start missed days still break - the gate only suppresses pre-start red`() =
        runTest {
            settings.setTrackingStartDate(june(1))
            markDays(june(1), june(2)) // June 3-8 passed unmarked - breaks
            markDays(june(9), june(10))
            val stats = useCase()().first()
            assertThat(stats.currentStreakDays).isEqualTo(2)
            assertThat(stats.longestStreakDays).isEqualTo(2)
        }

    // --- Future marks: never streak days, always counted in totals ---

    @Test
    fun `marks on future dates do not enter the streak walk but do count in year totals`() =
        runTest {
            markDays(june(9), june(10), june(15)) // June 15 is ahead of the fixed today
            val stats = useCase()().first()
            assertThat(stats.currentStreakDays).isEqualTo(2)
            assertThat(stats.longestStreakDays).isEqualTo(2)
            assertThat(stats.yearReadCount).isEqualTo(9)
        }

    @Test
    fun `only future marks - streaks are zero, totals still counted`() =
        runTest {
            markDays(june(20))
            val stats = useCase()().first()
            assertThat(stats.currentStreakDays).isEqualTo(0)
            assertThat(stats.longestStreakDays).isEqualTo(0)
            assertThat(stats.yearReadCount).isEqualTo(3)
        }

    // --- Year + per-stream totals (groups 3 and 4) ---

    @Test
    fun `year totals count only the current calendar year, per stream`() =
        runTest {
            progress.setWholeDay(LocalDate.of(2025, 12, 31), isRead = true) // other year: excluded
            progress.setRead(june(1), Stream.LAW_AND_HISTORY, isRead = true)
            progress.setRead(june(2), Stream.LAW_AND_HISTORY, isRead = true)
            progress.setRead(june(3), Stream.LAW_AND_HISTORY, isRead = true)
            progress.setRead(june(4), Stream.NEW_TESTAMENT, isRead = true)
            val stats = useCase()().first()
            assertThat(stats.yearReadCount).isEqualTo(4)
            assertThat(stats.streamReadCounts)
                .containsExactly(
                    Stream.LAW_AND_HISTORY,
                    3,
                    Stream.PSALMS_AND_PROPHECY,
                    0,
                    Stream.NEW_TESTAMENT,
                    1,
                )
            assertThat(stats.yearReadCount).isEqualTo(stats.streamReadCounts.values.sum())
        }

    // --- FR-17: pure live derivation (R-STREAK-6) ---

    @Test
    fun `an open stats collector receives a new emission when a day is marked`() =
        runTest {
            useCase()().test {
                assertThat(awaitItem().currentStreakDays).isEqualTo(0)
                markDays(june(10))
                assertThat(awaitItem().currentStreakDays).isEqualTo(1)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `re-collecting after marks and a start-date change derives consistent numbers`() =
        runTest {
            // FR-17 / R-STREAK-6: stats are re-derived from stored marks on every collection,
            // so marking days and moving the tracking start date are reflected immediately.
            val flow = useCase()()
            assertThat(flow.first().currentStreakDays).isEqualTo(0)
            markDays(june(10))
            assertThat(flow.first().currentStreakDays).isEqualTo(1)
            // June 9 passed unmarked: the run is cut off at 1 ...
            markDays(june(7), june(8))
            assertThat(flow.first().currentStreakDays).isEqualTo(1)
            // ... until a start date of June 10 makes June 9 neutral; the complete pre-start
            // June 7-8 keep their earned credit and the walk reconnects (R-STREAK-5).
            settings.setTrackingStartDate(june(10))
            assertThat(flow.first().currentStreakDays).isEqualTo(3)
        }
}

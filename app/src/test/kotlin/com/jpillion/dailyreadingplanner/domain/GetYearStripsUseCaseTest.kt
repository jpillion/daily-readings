package com.jpillion.dailyreadingplanner.domain

import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.core.date.ScheduleDateResolver
import com.jpillion.dailyreadingplanner.domain.model.Stream
import com.jpillion.dailyreadingplanner.domain.model.StripDayState
import com.jpillion.dailyreadingplanner.domain.model.YearStrips
import com.jpillion.dailyreadingplanner.testing.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * S17 (D-S17-2): per-(day, stream) strip states derived THROUGH the shared
 * [DayCompletionClassifier] — the pinned truth-table order (Feb 29 → marked/green →
 * pre-start gate → past/red → neutral) must hold per stream, including earned-green
 * parity for pre-start marked days. Default fixed "today" = 2026-06-10.
 */
class GetYearStripsUseCaseTest {
    private val clock = Clock.fixed(Instant.parse("2026-06-10T12:00:00Z"), ZoneOffset.UTC)
    private val progress = FakeProgressRepository()
    private val settings = FakeSettingsRepository()

    private fun useCase(at: Clock = clock) =
        GetYearStripsUseCase(
            DayCompletionClassifier(ScheduleDateResolver()),
            progress,
            settings,
            at,
        )

    private suspend fun strips(at: Clock = clock): YearStrips = useCase(at)().first()

    private fun YearStrips.stateOf(
        stream: Stream,
        date: LocalDate,
    ): StripDayState = dayStates.getValue(stream)[date.dayOfYear - 1]

    private fun june(day: Int): LocalDate = LocalDate.of(2026, 6, day)

    @Test
    fun `array covers the whole year - one entry per calendar day, all streams`() =
        runTest {
            val strips = strips()
            assertThat(strips.year).isEqualTo(2026)
            assertThat(strips.dayCount).isEqualTo(365) // 2026 is not a leap year
            assertThat(strips.dayStates.keys).containsExactlyElementsIn(Stream.entries)
            strips.dayStates.values.forEach { assertThat(it).hasSize(365) }
        }

    @Test
    fun `marked day is READ for exactly the marked stream`() =
        runTest {
            progress.setRead(june(5), Stream.NEW_TESTAMENT, isRead = true)
            val strips = strips()
            assertThat(strips.stateOf(Stream.NEW_TESTAMENT, june(5))).isEqualTo(StripDayState.READ)
            // The other streams on the same past day are unmarked: red, not green.
            assertThat(strips.stateOf(Stream.LAW_AND_HISTORY, june(5))).isEqualTo(StripDayState.MISSED)
            assertThat(strips.stateOf(Stream.PSALMS_AND_PROPHECY, june(5))).isEqualTo(StripDayState.MISSED)
        }

    @Test
    fun `past unmarked post-start day is MISSED - future unmarked day is NEUTRAL`() =
        runTest {
            val strips = strips()
            assertThat(strips.stateOf(Stream.LAW_AND_HISTORY, june(9))).isEqualTo(StripDayState.MISSED)
            assertThat(strips.stateOf(Stream.LAW_AND_HISTORY, june(11))).isEqualTo(StripDayState.NEUTRAL)
        }

    @Test
    fun `today unmarked is NEUTRAL - grace, never red while the day is still in progress`() =
        runTest {
            // Yesterday red proves the walk reaches this region; today must stay neutral.
            val strips = strips()
            assertThat(strips.stateOf(Stream.LAW_AND_HISTORY, june(10))).isEqualTo(StripDayState.NEUTRAL)
            assertThat(strips.stateOf(Stream.LAW_AND_HISTORY, june(9))).isEqualTo(StripDayState.MISSED)
        }

    @Test
    fun `today marked is READ - green does not wait for the day to pass`() =
        runTest {
            progress.setRead(june(10), Stream.PSALMS_AND_PROPHECY, isRead = true)
            assertThat(strips().stateOf(Stream.PSALMS_AND_PROPHECY, june(10))).isEqualTo(StripDayState.READ)
        }

    @Test
    fun `pre-start unmarked days are NEUTRAL - the gate suppresses red`() =
        runTest {
            settings.setTrackingStartDate(june(1))
            val strips = strips()
            assertThat(strips.stateOf(Stream.LAW_AND_HISTORY, LocalDate.of(2026, 3, 15)))
                .isEqualTo(StripDayState.NEUTRAL)
            // Boundary: the start date itself is post-start - past unmarked = red.
            assertThat(strips.stateOf(Stream.LAW_AND_HISTORY, june(1))).isEqualTo(StripDayState.MISSED)
        }

    @Test
    fun `pre-start marked day keeps its earned green - parity with the picker dots`() =
        runTest {
            settings.setTrackingStartDate(june(1))
            progress.setRead(LocalDate.of(2026, 3, 15), Stream.NEW_TESTAMENT, isRead = true)
            assertThat(strips().stateOf(Stream.NEW_TESTAMENT, LocalDate.of(2026, 3, 15)))
                .isEqualTo(StripDayState.READ)
        }

    @Test
    fun `Feb 29 is NEUTRAL in a leap year - never red`() =
        runTest {
            val leapClock = Clock.fixed(Instant.parse("2028-06-10T12:00:00Z"), ZoneOffset.UTC)
            val strips = strips(at = leapClock)
            assertThat(strips.dayCount).isEqualTo(366)
            assertThat(strips.stateOf(Stream.LAW_AND_HISTORY, LocalDate.of(2028, 2, 29)))
                .isEqualTo(StripDayState.NEUTRAL)
            // Its past unmarked neighbors ARE red - Feb 29 neutrality is the resolver, not range.
            assertThat(strips.stateOf(Stream.LAW_AND_HISTORY, LocalDate.of(2028, 2, 28)))
                .isEqualTo(StripDayState.MISSED)
            assertThat(strips.stateOf(Stream.LAW_AND_HISTORY, LocalDate.of(2028, 3, 1)))
                .isEqualTo(StripDayState.MISSED)
        }

    @Test
    fun `boundary day 1 - Jan 1 is classified, not skipped`() =
        runTest {
            progress.setRead(LocalDate.of(2026, 1, 1), Stream.LAW_AND_HISTORY, isRead = true)
            val strips = strips()
            assertThat(strips.stateOf(Stream.LAW_AND_HISTORY, LocalDate.of(2026, 1, 1)))
                .isEqualTo(StripDayState.READ)
            assertThat(strips.stateOf(Stream.NEW_TESTAMENT, LocalDate.of(2026, 1, 1)))
                .isEqualTo(StripDayState.MISSED)
        }

    @Test
    fun `boundary day 365 - Dec 31 is classified, not truncated`() =
        runTest {
            progress.setRead(LocalDate.of(2026, 12, 31), Stream.NEW_TESTAMENT, isRead = true)
            val strips = strips()
            assertThat(strips.stateOf(Stream.NEW_TESTAMENT, LocalDate.of(2026, 12, 31)))
                .isEqualTo(StripDayState.READ)
            // Unmarked Dec 31 is future from June: neutral, not missing from the array.
            assertThat(strips.stateOf(Stream.LAW_AND_HISTORY, LocalDate.of(2026, 12, 31)))
                .isEqualTo(StripDayState.NEUTRAL)
        }

    @Test
    fun `other-year marks never bleed into the displayed year`() =
        runTest {
            progress.setRead(LocalDate.of(2025, 6, 5), Stream.NEW_TESTAMENT, isRead = true)
            assertThat(strips().stateOf(Stream.NEW_TESTAMENT, june(5))).isEqualTo(StripDayState.MISSED)
        }

    @Test
    fun `todayIndex points at today's segment`() =
        runTest {
            assertThat(strips().todayIndex).isEqualTo(june(10).dayOfYear - 1)
        }

    @Test
    fun `an open collector re-emits when a day is marked`() =
        runTest {
            val flow = useCase()()
            assertThat(flow.first().stateOf(Stream.LAW_AND_HISTORY, june(9)))
                .isEqualTo(StripDayState.MISSED)
            progress.setRead(june(9), Stream.LAW_AND_HISTORY, isRead = true)
            assertThat(flow.first().stateOf(Stream.LAW_AND_HISTORY, june(9)))
                .isEqualTo(StripDayState.READ)
        }
}

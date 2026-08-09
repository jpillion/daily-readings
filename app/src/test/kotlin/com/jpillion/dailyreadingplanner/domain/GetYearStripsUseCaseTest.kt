package com.jpillion.dailyreadingplanner.domain

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import com.jpillion.dailyreadingplanner.core.date.ScheduleDateResolver
import com.jpillion.dailyreadingplanner.domain.model.StripDayState
import com.jpillion.dailyreadingplanner.domain.model.YearStrips
import com.jpillion.dailyreadingplanner.platform.DateProvider
import com.jpillion.dailyreadingplanner.platform.FakeDateProvider
import com.jpillion.dailyreadingplanner.testing.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.time.Instant

/**
 * S17 (D-S17-2): per-(day, stream) strip states derived THROUGH the shared
 * [DayCompletionClassifier] — the pinned truth-table order (Feb 29 → marked/green →
 * pre-start gate → past/red → neutral) must hold per stream, including earned-green
 * parity for pre-start marked days. Default fixed "today" = 2026-06-10.
 */
class GetYearStripsUseCaseTest {
    private val dateProvider =
        FakeDateProvider(LocalDate(2026, 6, 10), now = Instant.parse("2026-06-10T12:00:00Z"))
    private val progress = FakeProgressRepository()
    private val settings = FakeSettingsRepository()
    private val activePlan = FakeActivePlanRepository()

    private fun useCase(at: DateProvider = dateProvider) =
        GetYearStripsUseCase(
            DayCompletionClassifier(ScheduleDateResolver()),
            progress,
            settings,
            activePlan,
            at,
        )

    private suspend fun strips(at: DateProvider = dateProvider): YearStrips = useCase(at)().first()

    private fun YearStrips.stateOf(
        stream: Int,
        date: LocalDate,
    ): StripDayState = dayStates.getValue(stream)[date.dayOfYear - 1]

    private fun june(day: Int): LocalDate = LocalDate(2026, 6, day)

    @Test
    fun `array covers the whole year - one entry per calendar day - all streams`() =
        runTest {
            val strips = strips()
            assertThat(strips.year).isEqualTo(2026)
            assertThat(strips.dayCount).isEqualTo(365) // 2026 is not a leap year
            assertThat(strips.dayStates.keys).containsExactlyInAnyOrder(1, 2, 3)
            strips.dayStates.values.forEach { assertThat(it).hasSize(365) }
        }

    @Test
    fun `marked day is READ for exactly the marked stream`() =
        runTest {
            progress.setRead(june(5), 3, isRead = true)
            val strips = strips()
            assertThat(strips.stateOf(3, june(5))).isEqualTo(StripDayState.READ)
            // The other streams on the same past day are unmarked: red, not green.
            assertThat(strips.stateOf(1, june(5))).isEqualTo(StripDayState.MISSED)
            assertThat(strips.stateOf(2, june(5))).isEqualTo(StripDayState.MISSED)
        }

    @Test
    fun `past unmarked post-start day is MISSED - future unmarked day is NEUTRAL`() =
        runTest {
            val strips = strips()
            assertThat(strips.stateOf(1, june(9))).isEqualTo(StripDayState.MISSED)
            assertThat(strips.stateOf(1, june(11))).isEqualTo(StripDayState.NEUTRAL)
        }

    @Test
    fun `today unmarked is NEUTRAL - grace - never red while the day is still in progress`() =
        runTest {
            // Yesterday red proves the walk reaches this region; today must stay neutral.
            val strips = strips()
            assertThat(strips.stateOf(1, june(10))).isEqualTo(StripDayState.NEUTRAL)
            assertThat(strips.stateOf(1, june(9))).isEqualTo(StripDayState.MISSED)
        }

    @Test
    fun `today marked is READ - green does not wait for the day to pass`() =
        runTest {
            progress.setRead(june(10), 2, isRead = true)
            assertThat(strips().stateOf(2, june(10))).isEqualTo(StripDayState.READ)
        }

    @Test
    fun `pre-start unmarked days are NEUTRAL - the gate suppresses red`() =
        runTest {
            settings.setTrackingStartDate(june(1))
            val strips = strips()
            assertThat(strips.stateOf(1, LocalDate(2026, 3, 15)))
                .isEqualTo(StripDayState.NEUTRAL)
            // Boundary: the start date itself is post-start - past unmarked = red.
            assertThat(strips.stateOf(1, june(1))).isEqualTo(StripDayState.MISSED)
        }

    @Test
    fun `pre-start marked day keeps its earned green - parity with the picker dots`() =
        runTest {
            settings.setTrackingStartDate(june(1))
            progress.setRead(LocalDate(2026, 3, 15), 3, isRead = true)
            assertThat(strips().stateOf(3, LocalDate(2026, 3, 15)))
                .isEqualTo(StripDayState.READ)
        }

    @Test
    fun `Feb 29 is NEUTRAL in a leap year - never red`() =
        runTest {
            val leapDateProvider =
                FakeDateProvider(LocalDate(2028, 6, 10), now = Instant.parse("2028-06-10T12:00:00Z"))
            val strips = strips(at = leapDateProvider)
            assertThat(strips.dayCount).isEqualTo(366)
            assertThat(strips.stateOf(1, LocalDate(2028, 2, 29)))
                .isEqualTo(StripDayState.NEUTRAL)
            // Its past unmarked neighbors ARE red - Feb 29 neutrality is the resolver, not range.
            assertThat(strips.stateOf(1, LocalDate(2028, 2, 28)))
                .isEqualTo(StripDayState.MISSED)
            assertThat(strips.stateOf(1, LocalDate(2028, 3, 1)))
                .isEqualTo(StripDayState.MISSED)
        }

    @Test
    fun `boundary day 1 - Jan 1 is classified - not skipped`() =
        runTest {
            progress.setRead(LocalDate(2026, 1, 1), 1, isRead = true)
            val strips = strips()
            assertThat(strips.stateOf(1, LocalDate(2026, 1, 1)))
                .isEqualTo(StripDayState.READ)
            assertThat(strips.stateOf(3, LocalDate(2026, 1, 1)))
                .isEqualTo(StripDayState.MISSED)
        }

    @Test
    fun `boundary day 365 - Dec 31 is classified - not truncated`() =
        runTest {
            progress.setRead(LocalDate(2026, 12, 31), 3, isRead = true)
            val strips = strips()
            assertThat(strips.stateOf(3, LocalDate(2026, 12, 31)))
                .isEqualTo(StripDayState.READ)
            // Unmarked Dec 31 is future from June: neutral, not missing from the array.
            assertThat(strips.stateOf(1, LocalDate(2026, 12, 31)))
                .isEqualTo(StripDayState.NEUTRAL)
        }

    @Test
    fun `other-year marks never bleed into the displayed year`() =
        runTest {
            progress.setRead(LocalDate(2025, 6, 5), 3, isRead = true)
            assertThat(strips().stateOf(3, june(5))).isEqualTo(StripDayState.MISSED)
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
            assertThat(flow.first().stateOf(1, june(9)))
                .isEqualTo(StripDayState.MISSED)
            progress.setRead(june(9), 1, isRead = true)
            assertThat(flow.first().stateOf(1, june(9)))
                .isEqualTo(StripDayState.READ)
        }
}

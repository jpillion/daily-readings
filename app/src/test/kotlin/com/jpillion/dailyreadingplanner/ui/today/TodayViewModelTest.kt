package com.jpillion.dailyreadingplanner.ui.today

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.core.date.ReadingDate
import com.jpillion.dailyreadingplanner.core.date.ScheduleDateResolver
import com.jpillion.dailyreadingplanner.data.plan.ReadingPlanRepository
import com.jpillion.dailyreadingplanner.data.reference.BlbUrlBuilder
import com.jpillion.dailyreadingplanner.domain.FakeProgressRepository
import com.jpillion.dailyreadingplanner.domain.FakeReadingPlanRepository
import com.jpillion.dailyreadingplanner.domain.GetDayReadingsUseCase
import com.jpillion.dailyreadingplanner.domain.MarkWholeDayUseCase
import com.jpillion.dailyreadingplanner.domain.OpenReferenceUseCase
import com.jpillion.dailyreadingplanner.domain.ToggleReadingUseCase
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.Stream
import com.jpillion.dailyreadingplanner.domain.threePortions
import com.jpillion.dailyreadingplanner.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

class TodayViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val progress = FakeProgressRepository()

    private fun clockAt(date: LocalDate): Clock =
        Clock.fixed(date.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)

    private fun viewModel(
        date: LocalDate = LocalDate.of(2026, 6, 10),
        planRepository: ReadingPlanRepository = FakeReadingPlanRepository(),
    ): TodayViewModel {
        val resolver = ScheduleDateResolver()
        return TodayViewModel(
            getDayReadings = GetDayReadingsUseCase(resolver, planRepository, progress),
            toggleReading = ToggleReadingUseCase(progress),
            markWholeDay = MarkWholeDayUseCase(progress),
            openReference = OpenReferenceUseCase(BlbUrlBuilder()),
            clock = clockAt(date),
        )
    }

    @Test
    fun `scheduled day loads three unread readings`() =
        runTest {
            val vm = viewModel()
            vm.uiState.test {
                val state = awaitScheduled()
                assertThat(state.date).isEqualTo(LocalDate.of(2026, 6, 10))
                assertThat(state.readings).hasSize(3)
                assertThat(state.readings.map { it.portion.stream })
                    .containsExactly(Stream.LAW_AND_HISTORY, Stream.PSALMS_AND_PROPHECY, Stream.NEW_TESTAMENT)
                    .inOrder()
                assertThat(state.readings.none { it.isRead }).isTrue()
                assertThat(state.dayComplete).isFalse()
            }
        }

    @Test
    fun `toggling an unread reading marks it read and updates state`() =
        runTest {
            val vm = viewModel()
            vm.uiState.test {
                val initial = awaitScheduled()
                vm.onToggleReading(initial.readings[1])
                val updated = awaitScheduled()
                assertThat(updated.readings[1].isRead).isTrue()
                assertThat(updated.readings[0].isRead).isFalse()
                assertThat(updated.readings[2].isRead).isFalse()
                assertThat(updated.dayComplete).isFalse()
            }
        }

    @Test
    fun `toggling a read reading unmarks it`() =
        runTest {
            progress.setRead(LocalDate.of(2026, 6, 10), Stream.NEW_TESTAMENT, true)
            val vm = viewModel()
            vm.uiState.test {
                val initial = awaitScheduled()
                assertThat(initial.readings[2].isRead).isTrue()
                vm.onToggleReading(initial.readings[2])
                val updated = awaitScheduled()
                assertThat(updated.readings[2].isRead).isFalse()
            }
        }

    @Test
    fun `mark whole day marks all three readings in one tap`() =
        runTest {
            val vm = viewModel()
            vm.uiState.test {
                awaitScheduled()
                vm.onMarkWholeDay()
                val updated = awaitScheduled()
                assertThat(updated.readings.all { it.isRead }).isTrue()
                assertThat(updated.dayComplete).isTrue()
            }
        }

    @Test
    fun `mark whole day on a complete day unmarks all three`() =
        runTest {
            progress.setWholeDay(LocalDate.of(2026, 6, 10), true)
            val vm = viewModel()
            vm.uiState.test {
                val initial = awaitScheduled()
                assertThat(initial.dayComplete).isTrue()
                vm.onMarkWholeDay()
                val updated = awaitScheduled()
                assertThat(updated.readings.none { it.isRead }).isTrue()
                assertThat(updated.dayComplete).isFalse()
            }
        }

    @Test
    fun `Feb 29 resolves to the no-scheduled-readings state`() =
        runTest {
            val vm = viewModel(date = LocalDate.of(2028, 2, 29))
            vm.uiState.test {
                val state = awaitNonLoading()
                assertThat(state).isInstanceOf(TodayUiState.NoScheduledReadings::class.java)
                assertThat((state as TodayUiState.NoScheduledReadings).date)
                    .isEqualTo(LocalDate.of(2028, 2, 29))
            }
            // D1: no progress is ever queried or written for Feb 29.
            assertThat(progress.marksFor(LocalDate.of(2028, 2, 29))).isEmpty()
        }

    @Test
    fun `plan load failure degrades to LoadFailed and retry recovers`() =
        runTest {
            val flaky = FlakyPlanRepository()
            val vm = viewModel(planRepository = flaky)
            vm.uiState.test {
                val failed = awaitNonLoading()
                assertThat(failed).isInstanceOf(TodayUiState.LoadFailed::class.java)
                flaky.failing = false
                vm.onRetry()
                val recovered = awaitScheduled()
                assertThat(recovered.readings).hasSize(3)
            }
        }

    @Test
    fun `tapping a reading emits its BLB URL for the first ref`() =
        runTest {
            val vm = viewModel()
            vm.uiState.test {
                val state = awaitScheduled()
                vm.openUrlEvents.test {
                    vm.onReadingTapped(state.readings[0].portion)
                    assertThat(awaitItem()).isEqualTo("https://www.blueletterbible.org/kjv/gen/1/")
                    vm.onReadingTapped(state.readings[2].portion)
                    assertThat(awaitItem()).isEqualTo("https://www.blueletterbible.org/kjv/mat/1/")
                }
            }
        }

    private suspend fun app.cash.turbine.ReceiveTurbine<TodayUiState>.awaitNonLoading(): TodayUiState {
        var state = awaitItem()
        while (state is TodayUiState.Loading) state = awaitItem()
        return state
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<TodayUiState>.awaitScheduled(): TodayUiState.Scheduled {
        val state = awaitNonLoading()
        assertThat(state).isInstanceOf(TodayUiState.Scheduled::class.java)
        return state as TodayUiState.Scheduled
    }
}

/** A plan repository whose asset read can be flipped between failing and healthy. */
private class FlakyPlanRepository : ReadingPlanRepository {
    var failing = true

    override suspend fun portionsFor(date: ReadingDate): List<Portion> =
        if (failing) error("simulated corrupt asset") else threePortions
}

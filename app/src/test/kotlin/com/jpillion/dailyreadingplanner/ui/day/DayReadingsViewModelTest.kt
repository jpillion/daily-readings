package com.jpillion.dailyreadingplanner.ui.day

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.core.date.ReadingDate
import com.jpillion.dailyreadingplanner.core.date.ScheduleDateResolver
import com.jpillion.dailyreadingplanner.data.plan.ReadingPlanRepository
import com.jpillion.dailyreadingplanner.data.reference.BlbUrlBuilder
import com.jpillion.dailyreadingplanner.domain.FakeProgressRepository
import com.jpillion.dailyreadingplanner.domain.FakeReadingPlanRepository
import com.jpillion.dailyreadingplanner.domain.GetDayReadingsUseCase
import com.jpillion.dailyreadingplanner.domain.GetMonthCompletionUseCase
import com.jpillion.dailyreadingplanner.domain.MarkWholeDayUseCase
import com.jpillion.dailyreadingplanner.domain.OpenReferenceUseCase
import com.jpillion.dailyreadingplanner.domain.ToggleReadingUseCase
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.Stream
import com.jpillion.dailyreadingplanner.domain.threePortions
import com.jpillion.dailyreadingplanner.testing.FakeWidgetRefresher
import com.jpillion.dailyreadingplanner.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

class DayReadingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val progress = FakeProgressRepository()
    private val widgetRefresher = FakeWidgetRefresher()
    private val today = LocalDate.of(2026, 6, 10)

    private fun clockAt(date: LocalDate): Clock =
        Clock.fixed(date.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)

    private fun viewModel(
        date: LocalDate = today,
        planRepository: ReadingPlanRepository = FakeReadingPlanRepository(),
    ): DayReadingsViewModel {
        val resolver = ScheduleDateResolver()
        val clock = clockAt(date)
        return DayReadingsViewModel(
            getDayReadings = GetDayReadingsUseCase(resolver, planRepository, progress),
            getMonthCompletion = GetMonthCompletionUseCase(resolver, progress, clock),
            toggleReading = ToggleReadingUseCase(progress),
            markWholeDay = MarkWholeDayUseCase(progress),
            openReference = OpenReferenceUseCase(BlbUrlBuilder()),
            widgetRefresher = widgetRefresher,
            clock = clock,
        )
    }

    @Test
    fun `today is pinned from the injected clock`() {
        assertThat(viewModel().today).isEqualTo(today)
    }

    @Test
    fun `scheduled day loads three unread readings`() =
        runTest {
            val vm = viewModel()
            vm.uiStateFor(today).test {
                val state = awaitScheduled()
                assertThat(state.date).isEqualTo(today)
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
            vm.uiStateFor(today).test {
                val initial = awaitScheduled()
                vm.onToggleReading(today, initial.readings[1])
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
            progress.setRead(today, Stream.NEW_TESTAMENT, true)
            val vm = viewModel()
            vm.uiStateFor(today).test {
                val initial = awaitScheduled()
                assertThat(initial.readings[2].isRead).isTrue()
                vm.onToggleReading(today, initial.readings[2])
                val updated = awaitScheduled()
                assertThat(updated.readings[2].isRead).isFalse()
            }
        }

    @Test
    fun `mark whole day marks all three readings in one tap`() =
        runTest {
            val vm = viewModel()
            vm.uiStateFor(today).test {
                val initial = awaitScheduled()
                vm.onMarkWholeDay(today, initial.dayComplete)
                val updated = awaitScheduled()
                assertThat(updated.readings.all { it.isRead }).isTrue()
                assertThat(updated.dayComplete).isTrue()
            }
        }

    @Test
    fun `mark whole day on a complete day unmarks all three`() =
        runTest {
            progress.setWholeDay(today, true)
            val vm = viewModel()
            vm.uiStateFor(today).test {
                val initial = awaitScheduled()
                assertThat(initial.dayComplete).isTrue()
                vm.onMarkWholeDay(today, initial.dayComplete)
                val updated = awaitScheduled()
                assertThat(updated.readings.none { it.isRead }).isTrue()
                assertThat(updated.dayComplete).isFalse()
            }
        }

    @Test
    fun `browsing another date yields that date's state and marks write to that actual date`() =
        runTest {
            val yesterday = today.minusDays(1)
            val vm = viewModel()
            vm.uiStateFor(yesterday).test {
                val initial = awaitScheduled()
                assertThat(initial.date).isEqualTo(yesterday)
                vm.onToggleReading(yesterday, initial.readings[0])
                val updated = awaitScheduled()
                assertThat(updated.readings[0].isRead).isTrue()
            }
            // D-S5-3: progress is keyed to the displayed full date — today is untouched.
            assertThat(progress.marksFor(yesterday)).containsExactly(Stream.LAW_AND_HISTORY)
            assertThat(progress.marksFor(today)).isEmpty()
        }

    @Test
    fun `each date's state flow is independent`() =
        runTest {
            val tomorrow = today.plusDays(1)
            val vm = viewModel()
            vm.uiStateFor(today).test {
                vm.onMarkWholeDay(today, false)
                awaitScheduledWhere { it.dayComplete }
            }
            vm.uiStateFor(tomorrow).test {
                val state = awaitScheduled()
                assertThat(state.date).isEqualTo(tomorrow)
                assertThat(state.dayComplete).isFalse()
            }
        }

    @Test
    fun `whole-day mark across the year boundary writes to the adjacent year's date`() =
        runTest {
            // D-S5-3: swiping steps real calendar days, so Dec 31 2026 -> Jan 1 *2027*.
            val newYearsEve = LocalDate.of(2026, 12, 31)
            val jan1Next = LocalDate.of(2027, 1, 1)
            val vm = viewModel(date = newYearsEve)
            vm.uiStateFor(jan1Next).test {
                val initial = awaitScheduled()
                vm.onMarkWholeDay(jan1Next, initial.dayComplete)
                val updated = awaitScheduled()
                assertThat(updated.dayComplete).isTrue()
            }
            assertThat(progress.marksFor(jan1Next)).isEqualTo(Stream.entries.toSet())
            // Year isolation: neither today nor the *current* year's Jan 1 got marked.
            assertThat(progress.marksFor(newYearsEve)).isEmpty()
            assertThat(progress.marksFor(LocalDate.of(2026, 1, 1))).isEmpty()
        }

    @Test
    fun `Feb 29 resolves to the no-scheduled-readings state with no progress touched`() =
        runTest {
            val feb29 = LocalDate.of(2028, 2, 29)
            val vm = viewModel(date = LocalDate.of(2028, 2, 28))
            vm.uiStateFor(feb29).test {
                val state = awaitNonLoading()
                assertThat(state).isInstanceOf(DayUiState.NoScheduledReadings::class.java)
                assertThat((state as DayUiState.NoScheduledReadings).date).isEqualTo(feb29)
            }
            // D1: no progress is ever queried or written for Feb 29.
            assertThat(progress.marksFor(feb29)).isEmpty()
        }

    @Test
    fun `plan load failure degrades to LoadFailed and retry recovers`() =
        runTest {
            val flaky = FlakyPlanRepository()
            val vm = viewModel(planRepository = flaky)
            vm.uiStateFor(today).test {
                val failed = awaitNonLoading()
                assertThat(failed).isInstanceOf(DayUiState.LoadFailed::class.java)
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
            vm.uiStateFor(today).test {
                val state = awaitScheduled()
                vm.openUrlEvents.test {
                    vm.onReadingTapped(state.readings[0].portion)
                    assertThat(awaitItem()).isEqualTo("https://www.blueletterbible.org/kjv/gen/1/")
                    vm.onReadingTapped(state.readings[2].portion)
                    assertThat(awaitItem()).isEqualTo("https://www.blueletterbible.org/kjv/mat/1/")
                }
            }
        }

    // --- Sprint 7: opportunistic widget refresh on progress change (D9, ESpec §7) ---

    @Test
    fun `toggling a reading refreshes the home-screen widget`() =
        runTest {
            val vm = viewModel()
            vm.uiStateFor(today).test {
                val initial = awaitScheduled()
                vm.onToggleReading(today, initial.readings[0])
                awaitScheduled()
            }
            assertThat(widgetRefresher.refreshCount).isEqualTo(1)
        }

    @Test
    fun `marking the whole day refreshes the home-screen widget`() =
        runTest {
            val vm = viewModel()
            vm.uiStateFor(today).test {
                val initial = awaitScheduled()
                vm.onMarkWholeDay(today, initial.dayComplete)
                awaitScheduledWhere { it.dayComplete }
            }
            assertThat(widgetRefresher.refreshCount).isEqualTo(1)
        }

    @Test
    fun `opening a reading on BLB does not refresh the widget`() =
        runTest {
            // Adversarial: only progress *mutations* refresh; a read-only tap must not.
            val vm = viewModel()
            vm.uiStateFor(today).test {
                val state = awaitScheduled()
                vm.openUrlEvents.test {
                    vm.onReadingTapped(state.readings[0].portion)
                    awaitItem()
                }
            }
            assertThat(widgetRefresher.refreshCount).isEqualTo(0)
        }

    private suspend fun app.cash.turbine.ReceiveTurbine<DayUiState>.awaitNonLoading(): DayUiState {
        var state = awaitItem()
        while (state is DayUiState.Loading) state = awaitItem()
        return state
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<DayUiState>.awaitScheduled(): DayUiState.Scheduled {
        val state = awaitNonLoading()
        assertThat(state).isInstanceOf(DayUiState.Scheduled::class.java)
        return state as DayUiState.Scheduled
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<DayUiState>.awaitScheduledWhere(
        predicate: (DayUiState.Scheduled) -> Boolean,
    ): DayUiState.Scheduled {
        var state = awaitScheduled()
        while (!predicate(state)) state = awaitScheduled()
        return state
    }
}

/** A plan repository whose asset read can be flipped between failing and healthy. */
private class FlakyPlanRepository : ReadingPlanRepository {
    var failing = true

    override suspend fun portionsFor(date: ReadingDate): List<Portion> =
        if (failing) error("simulated corrupt asset") else threePortions
}

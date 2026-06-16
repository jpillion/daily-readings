package com.jpillion.dailyreadingplanner.ui.day

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.core.date.ReadingDate
import com.jpillion.dailyreadingplanner.core.date.ScheduleDateResolver
import com.jpillion.dailyreadingplanner.data.plan.ReadingPlanRepository
import com.jpillion.dailyreadingplanner.data.reference.ProviderUrlBuilder
import com.jpillion.dailyreadingplanner.domain.CompleteReadingDestinationPromptUseCase
import com.jpillion.dailyreadingplanner.domain.CompleteTrackingStartPromptUseCase
import com.jpillion.dailyreadingplanner.domain.CompleteUpgradeNoteUseCase
import com.jpillion.dailyreadingplanner.domain.DayCompletionClassifier
import com.jpillion.dailyreadingplanner.domain.FakeActivePlanRepository
import com.jpillion.dailyreadingplanner.domain.FakeProgressRepository
import com.jpillion.dailyreadingplanner.domain.FakeReadingPlanRepository
import com.jpillion.dailyreadingplanner.domain.GetDayReadingsUseCase
import com.jpillion.dailyreadingplanner.domain.GetMonthCompletionUseCase
import com.jpillion.dailyreadingplanner.domain.GetReadingStatsUseCase
import com.jpillion.dailyreadingplanner.domain.GetYearStripsUseCase
import com.jpillion.dailyreadingplanner.domain.MarkWholeDayUseCase
import com.jpillion.dailyreadingplanner.domain.OpenReferenceUseCase
import com.jpillion.dailyreadingplanner.domain.ResolveReadingDestinationPromptUseCase
import com.jpillion.dailyreadingplanner.domain.ResolveTrackingStartPromptUseCase
import com.jpillion.dailyreadingplanner.domain.ResolveUpgradeNoteUseCase
import com.jpillion.dailyreadingplanner.domain.ToggleReadingUseCase
import com.jpillion.dailyreadingplanner.domain.model.ExternalBibleApp
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestination
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestinationMode
import com.jpillion.dailyreadingplanner.domain.threePortions
import com.jpillion.dailyreadingplanner.testing.FakeSettingsRepository
import com.jpillion.dailyreadingplanner.testing.FakeWidgetRefresher
import com.jpillion.dailyreadingplanner.testing.MainDispatcherRule
import com.jpillion.dailyreadingplanner.ui.navigation.ReaderHandoff
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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
    private val readerHandoff = ReaderHandoff()
    private val today = LocalDate.of(2026, 6, 10)
    private val activePlan = FakeActivePlanRepository()

    private fun clockAt(date: LocalDate): Clock =
        Clock.fixed(date.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)

    private fun viewModel(
        date: LocalDate = today,
        planRepository: ReadingPlanRepository = FakeReadingPlanRepository(),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
    ): DayReadingsViewModel {
        val resolver = ScheduleDateResolver()
        val clock = clockAt(date)
        val classifier = DayCompletionClassifier(resolver)
        return DayReadingsViewModel(
            getDayReadings = GetDayReadingsUseCase(resolver, planRepository, progress, activePlan),
            getMonthCompletion = GetMonthCompletionUseCase(classifier, progress, settings, activePlan, clock),
            toggleReading = ToggleReadingUseCase(progress, activePlan),
            markWholeDay = MarkWholeDayUseCase(progress, activePlan),
            openReference = OpenReferenceUseCase(settings, ProviderUrlBuilder()),
            widgetRefresher = widgetRefresher,
            readerHandoff = readerHandoff,
            completeTrackingStartPrompt = CompleteTrackingStartPromptUseCase(settings),
            resolveTrackingStartPrompt = ResolveTrackingStartPromptUseCase(settings, progress),
            completeReadingDestinationPrompt = CompleteReadingDestinationPromptUseCase(settings),
            resolveReadingDestinationPrompt = ResolveReadingDestinationPromptUseCase(settings, progress),
            completeUpgradeNote = CompleteUpgradeNoteUseCase(settings),
            resolveUpgradeNote = ResolveUpgradeNoteUseCase(settings, progress),
            getReadingStats = GetReadingStatsUseCase(classifier, progress, settings, activePlan, clock),
            getYearStrips = GetYearStripsUseCase(classifier, progress, settings, activePlan, clock),
            settingsRepository = settings,
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
                assertThat(state.readings.map { it.portion.streamNumber })
                    .containsExactly(1, 2, 3)
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
            progress.setRead(today, 3, true)
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
            progress.setWholeDay(today, listOf(1, 2, 3), true)
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
            assertThat(progress.marksFor(yesterday)).containsExactly(1)
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
            assertThat(progress.marksFor(jan1Next)).isEqualTo(setOf(1, 2, 3))
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
    fun `tapping a reading emits a web destination with its BLB URL for the first ref`() =
        runTest {
            val vm = viewModel()
            vm.uiStateFor(today).test {
                val state = awaitScheduled()
                vm.openDestinationEvents.test {
                    vm.onReadingTapped(state.readings[0].portion)
                    assertThat(awaitItem())
                        .isEqualTo(ReadingDestination.Web("https://www.blueletterbible.org/kjv/gen/1/"))
                    vm.onReadingTapped(state.readings[2].portion)
                    assertThat(awaitItem())
                        .isEqualTo(ReadingDestination.Web("https://www.blueletterbible.org/kjv/mat/1/"))
                }
            }
        }

    @Test
    fun `with MySword chosen a tap emits the app destination carrying the BLB fallback`() =
        runTest {
            // S15 (D-S15-1/3): numeric vendor form for the intent URL; BLB as the
            // uninstalled-fallback so a dead tap can never land on the mysword.info stub.
            val settings = FakeSettingsRepository()
            settings.setExternalBibleApp(ExternalBibleApp.MYSWORD)
            val vm = viewModel(settings = settings)
            vm.uiStateFor(today).test {
                val state = awaitScheduled()
                vm.openDestinationEvents.test {
                    vm.onReadingTapped(state.readings[0].portion)
                    assertThat(awaitItem())
                        .isEqualTo(
                            ReadingDestination.MySwordApp(
                                url = "https://mysword.info/b?r=1.1",
                                fallbackUrl = "https://www.blueletterbible.org/kjv/gen/1/",
                            ),
                        )
                }
            }
        }

    @Test
    fun `with the in-app reader chosen a tap hands off the portion and signals navigation`() =
        runTest {
            // VD-T5 (D-V3-18, D-D-1): IN_APP is a navigation target, not an OS launch. The tap
            // publishes the portion to the handoff seam and raises openReaderEvents; it must NOT
            // emit on openDestinationEvents (the Web/MySword OS-launch path).
            val settings = FakeSettingsRepository()
            settings.setReadingDestinationMode(ReadingDestinationMode.IN_APP)
            val vm = viewModel(settings = settings)
            vm.uiStateFor(today).test {
                val state = awaitScheduled()
                vm.openReaderEvents.test {
                    vm.onReadingTapped(state.readings[0].portion)
                    awaitItem() // the navigate-to-Bible-tab signal
                    assertThat(readerHandoff.pending.value).isEqualTo(state.readings[0].portion)
                }
            }
        }

    @Test
    fun `an in-app tap does not emit on the OS-launch destination channel`() =
        runTest {
            val settings = FakeSettingsRepository()
            settings.setReadingDestinationMode(ReadingDestinationMode.IN_APP)
            val vm = viewModel(settings = settings)
            vm.uiStateFor(today).test {
                val state = awaitScheduled()
                vm.openDestinationEvents.test {
                    vm.onReadingTapped(state.readings[0].portion)
                    expectNoEvents()
                }
            }
        }

    // --- Sprint 15: the inline stats panel (D-S15-4/5) ---

    @Test
    fun `stats panel starts null then exposes the live derivation with streaks hidden by default`() =
        runTest {
            // S18: streaks are opt-in — the default panel ships with showStreaks = false.
            progress.setWholeDay(today.minusDays(1), listOf(1, 2, 3), true)
            progress.setWholeDay(today, listOf(1, 2, 3), true)
            val vm = viewModel()
            assertThat(vm.statsPanel.value).isNull()
            val panel = vm.statsPanel.filterNotNull().first()
            assertThat(panel.showStreaks).isFalse()
            assertThat(panel.stats.currentStreakDays).isEqualTo(2)
            assertThat(panel.stats.yearReadCount).isEqualTo(6)
        }

    @Test
    fun `stats panel carries the year strips - marked yesterday is READ on every stream`() =
        runTest {
            // S17: the same panel emission carries the strip day-states, live from marks.
            progress.setWholeDay(today.minusDays(1), listOf(1, 2, 3), true)
            val vm = viewModel()
            val strips =
                vm.statsPanel
                    .filterNotNull()
                    .first()
                    .strips
            assertThat(strips.year).isEqualTo(today.year)
            assertThat(strips.todayIndex).isEqualTo(today.dayOfYear - 1)
            val yesterdayIndex = today.dayOfYear - 2
            listOf(1, 2, 3).forEach { streamNumber ->
                assertThat(strips.dayStates.getValue(streamNumber)[yesterdayIndex])
                    .isEqualTo(com.jpillion.dailyreadingplanner.domain.model.StripDayState.READ)
            }
        }

    @Test
    fun `stats panel reflects the show-streaks setting live`() =
        runTest {
            val settings = FakeSettingsRepository()
            val vm = viewModel(settings = settings)
            assertThat(
                vm.statsPanel
                    .filterNotNull()
                    .first()
                    .showStreaks,
            ).isFalse()
            settings.setShowStreaks(true)
            assertThat(
                vm.statsPanel
                    .filterNotNull()
                    .first { it.showStreaks }
                    .showStreaks,
            ).isTrue()
        }

    @Test
    fun `destinationMode and externalApp reflect the stored settings reactively`() =
        runTest {
            val settings = FakeSettingsRepository()
            val vm = viewModel(settings = settings)
            // Default install: external mode, Blue Letter Bible.
            assertThat(vm.destinationMode.first()).isEqualTo(ReadingDestinationMode.EXTERNAL)
            assertThat(vm.externalApp.first()).isEqualTo(ExternalBibleApp.BLB)
            // A Settings change is reflected live (the reading-tile hint follows it).
            settings.setReadingDestinationMode(ReadingDestinationMode.IN_APP)
            assertThat(
                vm.destinationMode.first { it == ReadingDestinationMode.IN_APP },
            ).isEqualTo(ReadingDestinationMode.IN_APP)
            settings.setExternalBibleApp(ExternalBibleApp.YOUVERSION)
            assertThat(
                vm.externalApp.first { it == ExternalBibleApp.YOUVERSION },
            ).isEqualTo(ExternalBibleApp.YOUVERSION)
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
                vm.openDestinationEvents.test {
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

    // --- S19: first-run tracking-start prompt (D-S19-1/2). ---

    @Test
    fun `fresh install shows the tracking-start prompt`() =
        runTest {
            val settings = FakeSettingsRepository()
            val vm = viewModel(settings = settings)
            vm.showTrackingStartPrompt.test {
                assertThat(awaitItem()).isTrue()
            }
            // Resolution alone persists nothing — an unanswered prompt re-asks next launch.
            assertThat(settings.storedTrackingStartInitialized.value).isFalse()
        }

    @Test
    fun `already-initialized device never sees the prompt`() =
        runTest {
            val settings = FakeSettingsRepository()
            settings.markTrackingStartInitialized()
            val vm = viewModel(settings = settings)
            vm.showTrackingStartPrompt.test {
                assertThat(awaitItem()).isFalse()
            }
        }

    @Test
    fun `choosing a date persists it, sets the marker, and hides the prompt`() =
        runTest {
            val settings = FakeSettingsRepository()
            val vm = viewModel(settings = settings)
            vm.showTrackingStartPrompt.test {
                assertThat(awaitItem()).isTrue()
                vm.onTrackingStartChosen(LocalDate.of(2026, 6, 10))
                assertThat(awaitItem()).isFalse()
            }
            assertThat(settings.storedTrackingStartDate.value).isEqualTo(LocalDate.of(2026, 6, 10))
            assertThat(settings.storedTrackingStartInitialized.value).isTrue()
        }

    @Test
    fun `dismissing without choosing applies the Jan-1 fallback and never re-shows`() =
        runTest {
            val settings = FakeSettingsRepository()
            val vm = viewModel(settings = settings)
            vm.showTrackingStartPrompt.test {
                assertThat(awaitItem()).isTrue()
                vm.onTrackingStartPromptDismissed()
                assertThat(awaitItem()).isFalse()
            }
            // D-S19-1: dismiss == the superseded D-S14-1 default, marker set => never again.
            assertThat(settings.storedTrackingStartDate.value).isEqualTo(LocalDate.of(2026, 1, 1))
            assertThat(settings.storedTrackingStartInitialized.value).isTrue()
        }
}

/** A plan repository whose asset read can be flipped between failing and healthy. */
private class FlakyPlanRepository : ReadingPlanRepository {
    var failing = true

    override suspend fun portionsFor(
        planId: String,
        date: ReadingDate,
    ): List<Portion> = if (failing) error("simulated corrupt asset") else threePortions

    override suspend fun descriptor(planId: String) =
        com.jpillion.dailyreadingplanner.domain.model.PlanDescriptor(
            planId = planId,
            name = "Bible Companion",
            anchoring = "DATE",
            dayCount = 365,
            streams =
                listOf(
                    com.jpillion.dailyreadingplanner.domain.model
                        .StreamDescriptor(1, "Law & History"),
                    com.jpillion.dailyreadingplanner.domain.model
                        .StreamDescriptor(2, "Psalms & Prophecy"),
                    com.jpillion.dailyreadingplanner.domain.model
                        .StreamDescriptor(3, "New Testament"),
                ),
        )
}

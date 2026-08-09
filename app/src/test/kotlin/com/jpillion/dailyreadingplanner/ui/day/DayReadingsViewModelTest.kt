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
import com.jpillion.dailyreadingplanner.domain.GetPartialSegmentsUseCase
import com.jpillion.dailyreadingplanner.domain.GetReadingStatsUseCase
import com.jpillion.dailyreadingplanner.domain.GetYearStripsUseCase
import com.jpillion.dailyreadingplanner.domain.MarkReadOnOpenUseCase
import com.jpillion.dailyreadingplanner.domain.MarkSegmentReadOnOpenUseCase
import com.jpillion.dailyreadingplanner.domain.MarkWholeDayUseCase
import com.jpillion.dailyreadingplanner.domain.OpenReferenceUseCase
import com.jpillion.dailyreadingplanner.domain.ResolveReadingDestinationPromptUseCase
import com.jpillion.dailyreadingplanner.domain.ResolveTrackingStartPromptUseCase
import com.jpillion.dailyreadingplanner.domain.ResolveUpgradeNoteUseCase
import com.jpillion.dailyreadingplanner.domain.ToggleReadingUseCase
import com.jpillion.dailyreadingplanner.domain.ToggleSegmentCheckUseCase
import com.jpillion.dailyreadingplanner.domain.model.ExternalBibleApp
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.ReadingCheckState
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestination
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestinationMode
import com.jpillion.dailyreadingplanner.domain.portion
import com.jpillion.dailyreadingplanner.domain.threePortions
import com.jpillion.dailyreadingplanner.testing.FakePartialReadingRepository
import com.jpillion.dailyreadingplanner.testing.FakeSettingsRepository
import com.jpillion.dailyreadingplanner.testing.FakeWidgetRefresher
import com.jpillion.dailyreadingplanner.testing.MainDispatcherRule
import com.jpillion.dailyreadingplanner.ui.navigation.ReaderHandoff
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.net.URLDecoder
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

class DayReadingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val progress = FakeProgressRepository()
    private val partials = FakePartialReadingRepository()
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
            getPartialSegments = GetPartialSegmentsUseCase(partials, activePlan),
            // The segment-level use cases are the REAL ones over the same fakes (sprint-00P), so
            // these tests exercise the pure policy + both stores end to end, not a stub.
            toggleSegmentCheck =
                ToggleSegmentCheckUseCase(ToggleReadingUseCase(progress, activePlan), partials, activePlan),
            markSegmentReadOnOpen =
                MarkSegmentReadOnOpenUseCase(MarkReadOnOpenUseCase(progress, activePlan), partials, activePlan),
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
                // sprint-00P: each Bible-Companion reading is ONE contiguous passage, so the three
                // readings still yield exactly three cards (segmentCount 1 => PARTIAL unreachable).
                assertThat(state.segments).hasSize(3)
                assertThat(state.segments.map { it.streamNumber })
                    .containsExactly(1, 2, 3)
                    .inOrder()
                assertThat(state.segments.map { it.segmentCount }).containsExactly(1, 1, 1)
                assertThat(state.segments.all { it.checkState == ReadingCheckState.UNCHECKED }).isTrue()
                assertThat(state.dayComplete).isFalse()
            }
        }

    @Test
    fun `toggling an unread reading marks it read and updates state`() =
        runTest {
            val vm = viewModel()
            vm.uiStateFor(today).test {
                val initial = awaitScheduled()
                vm.onToggleSegment(today, initial.segments[1])
                val updated = awaitScheduled()
                assertThat(updated.segments[1].checkState).isEqualTo(ReadingCheckState.COMPLETE)
                assertThat(updated.segments[0].checkState).isEqualTo(ReadingCheckState.UNCHECKED)
                assertThat(updated.segments[2].checkState).isEqualTo(ReadingCheckState.UNCHECKED)
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
                assertThat(initial.segments[2].checkState).isEqualTo(ReadingCheckState.COMPLETE)
                vm.onToggleSegment(today, initial.segments[2])
                val updated = awaitScheduled()
                assertThat(updated.segments[2].checkState).isEqualTo(ReadingCheckState.UNCHECKED)
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
                assertThat(updated.segments.all { it.checkState == ReadingCheckState.COMPLETE }).isTrue()
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
                assertThat(updated.segments.all { it.checkState == ReadingCheckState.UNCHECKED }).isTrue()
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
                vm.onToggleSegment(yesterday, initial.segments[0])
                val updated = awaitScheduled()
                assertThat(updated.segments[0].checkState).isEqualTo(ReadingCheckState.COMPLETE)
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
                assertThat(recovered.segments).hasSize(3)
            }
        }

    @Test
    fun `tapping a reading emits a web destination with its BLB URL for the first ref`() =
        runTest {
            val vm = viewModel()
            vm.uiStateFor(today).test {
                val state = awaitScheduled()
                vm.openDestinationEvents.test {
                    vm.onSegmentTapped(today, state.segments[0])
                    assertThat(awaitItem())
                        .isEqualTo(ReadingDestination.Web("https://www.blueletterbible.org/kjv/gen/1/"))
                    vm.onSegmentTapped(today, state.segments[2])
                    assertThat(awaitItem())
                        .isEqualTo(ReadingDestination.Web("https://www.blueletterbible.org/kjv/mat/1/"))
                }
                // sprint-00O: the tap now marks the readings read, so uiStateFor re-emits; this
                // test only asserts the destination, so ignore the trailing state update.
                cancelAndIgnoreRemainingEvents()
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
                    vm.onSegmentTapped(today, state.segments[0])
                    assertThat(awaitItem())
                        .isEqualTo(
                            ReadingDestination.MySwordApp(
                                url = "https://mysword.info/b?r=1.1",
                                fallbackUrl = "https://www.blueletterbible.org/kjv/gen/1/",
                            ),
                        )
                }
                // sprint-00O: ignore the mark-on-open state re-emission (asserted elsewhere).
                cancelAndIgnoreRemainingEvents()
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
                    vm.onSegmentTapped(today, state.segments[0])
                    awaitItem() // the navigate-to-Bible-tab signal
                    assertThat(readerHandoff.pending.value).isEqualTo(state.segments[0].portion)
                }
                // sprint-00O: ignore the mark-on-open state re-emission (asserted elsewhere).
                cancelAndIgnoreRemainingEvents()
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
                    vm.onSegmentTapped(today, state.segments[0])
                    expectNoEvents()
                }
                // sprint-00O: ignore the mark-on-open state re-emission (asserted elsewhere).
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- Sprint 00O: tap-to-open marks the reading read (D-O-1/2/4) ---

    @Test
    fun `tapping an unread reading marks it read for the displayed date`() =
        runTest {
            // Test 3a: opening a reading SETS it read (one-way side effect), for the displayed date.
            val vm = viewModel()
            vm.uiStateFor(today).test {
                val state = awaitScheduled()
                assertThat(state.segments[1].checkState).isEqualTo(ReadingCheckState.UNCHECKED)
                vm.openDestinationEvents.test {
                    vm.onSegmentTapped(today, state.segments[1])
                    awaitItem() // the destination still opens (proven separately in 3e)
                }
                // The tapped stream (number 2) is now read for today.
                val updated = awaitScheduledWhere { it.segments[1].checkState == ReadingCheckState.COMPLETE }
                assertThat(updated.segments[1].checkState).isEqualTo(ReadingCheckState.COMPLETE)
            }
            assertThat(progress.marksFor(today)).containsExactly(2)
        }

    @Test
    fun `tapping an already-read reading leaves it read - idempotent - never unmarks`() =
        runTest {
            // Test 3b: mark-on-open is one-way. An already-read reading stays read after a tap;
            // a second tap never toggles it off (the checkbox is the only un-mark affordance).
            progress.setRead(today, 1, true)
            val vm = viewModel()
            vm.uiStateFor(today).test {
                val initial = awaitScheduled()
                assertThat(initial.segments[0].checkState).isEqualTo(ReadingCheckState.COMPLETE)
                vm.openDestinationEvents.test {
                    vm.onSegmentTapped(today, initial.segments[0])
                    awaitItem()
                    // Tap the same already-read reading again — still must not unmark.
                    vm.onSegmentTapped(today, initial.segments[0])
                    awaitItem()
                }
                // Idempotent re-writes don't change the day state (distinctUntilChanged), but be
                // robust: ignore any trailing emission rather than depend on that.
                cancelAndIgnoreRemainingEvents()
            }
            // Stream 1 is still the only mark for today; nothing was unmarked.
            assertThat(progress.marksFor(today)).containsExactly(1)
        }

    @Test
    fun `tapping a reading on another date marks that actual date - not today`() =
        runTest {
            // Test 3c: the mark is keyed to the displayed full date (D-S5-3), mirroring the
            // browsing-another-date toggle test. Today must be untouched.
            val yesterday = today.minusDays(1)
            val vm = viewModel()
            vm.uiStateFor(yesterday).test {
                val state = awaitScheduled()
                assertThat(state.date).isEqualTo(yesterday)
                vm.openDestinationEvents.test {
                    vm.onSegmentTapped(yesterday, state.segments[0])
                    awaitItem()
                }
                val updated = awaitScheduledWhere { it.segments[0].checkState == ReadingCheckState.COMPLETE }
                assertThat(updated.segments[0].checkState).isEqualTo(ReadingCheckState.COMPLETE)
            }
            assertThat(progress.marksFor(yesterday)).containsExactly(1)
            assertThat(progress.marksFor(today)).isEmpty()
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
                vm.onToggleSegment(today, initial.segments[0])
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
    fun `opening a reading on BLB now refreshes the widget - intended D-O-4 behavior change`() =
        runTest {
            // INTENDED BEHAVIOR CHANGE (sprint-00O, D-O-1/4): tapping a reading to open it now
            // SETS it read as a side-effect (mark-on-open), which is a progress *mutation* — so it
            // refreshes the widget. This supersedes the old "a read-only tap must not refresh"
            // assertion: a tap is no longer read-only. Test 3d.
            val vm = viewModel()
            vm.uiStateFor(today).test {
                val state = awaitScheduled()
                vm.openDestinationEvents.test {
                    vm.onSegmentTapped(today, state.segments[0])
                    awaitItem()
                }
                // The mark-on-open re-emits the day state (stream 1 now read); ignore it here.
                cancelAndIgnoreRemainingEvents()
            }
            assertThat(widgetRefresher.refreshCount).isEqualTo(1)
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
    fun `choosing a date persists it - sets the marker - and hides the prompt`() =
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

    // --- sprint-00P (SEG-6): the segment split, D-SEG-6, and the partial-check transitions ---

    /**
     * The Chronological 07/25 reading — `Isaiah 37, 38, 39, Psalms 76` — i.e. the P0 repro. Its refs
     * are NOT a contiguous ascending global-chapter run (Psalms precedes Isaiah in canon order),
     * which is exactly what made `ReadingPagerIndex` throw and the reader silently fall back to
     * Genesis 1 when the WHOLE portion was handed over.
     */
    private val isaiahPsalmsReading = portion(1, "Isaiah" to 37, "Isaiah" to 38, "Isaiah" to 39, "Psalms" to 76)

    private fun twoSegmentViewModel(settings: FakeSettingsRepository = FakeSettingsRepository()) =
        viewModel(planRepository = FakeReadingPlanRepository(listOf(isaiahPsalmsReading)), settings = settings)

    @Test
    fun `a reading with two passages becomes two segment cards`() =
        runTest {
            // D-SEG-1/D-SEG-8: the book change splits, so one portion renders as two cards, each
            // naming only its own run. Both carry the same stream number (the progress key).
            val vm = twoSegmentViewModel()
            vm.uiStateFor(today).test {
                val state = awaitScheduled()
                assertThat(state.segments).hasSize(2)
                assertThat(state.segments.map { ReadingFormatter.format(it.portion) })
                    .containsExactly("Isaiah 37–39", "Psalm 76")
                    .inOrder()
                assertThat(state.segments.map { it.segmentIndex }).containsExactly(0, 1).inOrder()
                assertThat(state.segments.map { it.segmentCount }).containsExactly(2, 2)
                assertThat(state.segments.map { it.streamNumber }).containsExactly(1, 1)
                // The title repeats on both cards of the multi-segment stream (spec).
                assertThat(state.segments.map { it.streamTitle }).containsExactly("Law & History", "Law & History")
            }
        }

    @Test
    fun `tapping the second card hands the in-app reader exactly that segment`() =
        runTest {
            // THE P0 PIN (D-SEG-6). What reaches ReaderHandoff must be the tapped run alone —
            // [Psalms 76] — never the four-ref portion (which cannot build a ReadingPagerIndex and
            // used to degrade to Genesis 1).
            val settings = FakeSettingsRepository()
            settings.setReadingDestinationMode(ReadingDestinationMode.IN_APP)
            val vm = twoSegmentViewModel(settings)
            vm.uiStateFor(today).test {
                val state = awaitScheduled()
                vm.openReaderEvents.test {
                    vm.onSegmentTapped(today, state.segments[1])
                    awaitItem() // the navigate-to-Bible-tab signal
                }
                val handed = readerHandoff.pending.value
                assertThat(handed).isNotNull()
                assertThat(handed!!.streamNumber).isEqualTo(1)
                assertThat(handed.refs.map { it.book.canonicalName to it.chapter })
                    .containsExactly("Psalms" to 76)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `tapping the first card opens only that run on Bible Gateway`() =
        runTest {
            // D-SEG-6's second consequence: an external URL now carries just the tapped run, so it
            // agrees with what the card says. Bible Gateway is the multi-ref-capable provider, so
            // the whole-portion regression would be visible in its search term.
            val settings = FakeSettingsRepository()
            settings.setExternalBibleApp(ExternalBibleApp.BIBLE_GATEWAY)
            val vm = twoSegmentViewModel(settings)
            vm.uiStateFor(today).test {
                val state = awaitScheduled()
                vm.openDestinationEvents.test {
                    vm.onSegmentTapped(today, state.segments[0])
                    val destination = awaitItem()
                    assertThat(destination)
                        .isEqualTo(
                            ReadingDestination.Web(
                                "https://www.biblegateway.com/passage/?search=Isaiah+37-39&version=KJV",
                            ),
                        )
                    val url = (destination as ReadingDestination.Web).url
                    assertThat(URLDecoder.decode(url, "UTF-8")).contains("Isaiah 37-39")
                    assertThat(url).doesNotContain("Psalm")
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `checking the segments one at a time goes PARTIAL then COMPLETE`() =
        runTest {
            // The transition table, driven through the REAL use cases and both stores: the first
            // check writes only a cosmetic token (nothing outside the day screen changes), the last
            // one sets the real stream mark and clears every token.
            val vm = twoSegmentViewModel()
            vm.uiStateFor(today).test {
                val initial = awaitScheduled()
                vm.onToggleSegment(today, initial.segments[0])
                val partial = awaitScheduledWhere { it.segments[0].checkState == ReadingCheckState.PARTIAL }
                assertThat(partial.segments[1].checkState).isEqualTo(ReadingCheckState.UNCHECKED)
                assertThat(partial.dayComplete).isFalse()
                // D-SEG-3: a partially-read reading is NOT read anywhere outside this screen.
                assertThat(progress.marksFor(today)).isEmpty()

                vm.onToggleSegment(today, partial.segments[1])
                val complete =
                    awaitScheduledWhere { state -> state.segments.all { it.checkState == ReadingCheckState.COMPLETE } }
                assertThat(complete.dayComplete).isTrue()
                assertThat(progress.marksFor(today)).containsExactly(1)
                assertThat(partials.stored.value).isEmpty()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `unchecking one segment of a COMPLETE reading leaves the other PARTIAL`() =
        runTest {
            // The last row of the table: clearing the real mark keeps every OTHER segment checked
            // (it was read) — only the tapped one is taken back.
            progress.setRead(today, 1, true)
            val vm = twoSegmentViewModel()
            vm.uiStateFor(today).test {
                val initial = awaitScheduled()
                assertThat(initial.segments.all { it.checkState == ReadingCheckState.COMPLETE }).isTrue()

                vm.onToggleSegment(today, initial.segments[0])
                val after = awaitScheduledWhere { it.segments[0].checkState == ReadingCheckState.UNCHECKED }
                assertThat(after.segments[1].checkState).isEqualTo(ReadingCheckState.PARTIAL)
                assertThat(after.dayComplete).isFalse()
                assertThat(progress.marksFor(today)).isEmpty()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `N==1 parity - a single-segment reading toggles the real mark and never reaches PARTIAL`() =
        runTest {
            // D-SEG-4: at one segment the behaviour is byte-for-byte pre-sprint — one card, the real
            // mark, and no token ever written.
            val vm = viewModel()
            vm.uiStateFor(today).test {
                val initial = awaitScheduled()
                assertThat(initial.segments).hasSize(3)
                assertThat(initial.segments.map { it.segmentCount }).containsExactly(1, 1, 1)

                vm.onToggleSegment(today, initial.segments[1])
                val after = awaitScheduledWhere { it.segments[1].checkState != ReadingCheckState.UNCHECKED }
                assertThat(after.segments[1].checkState).isEqualTo(ReadingCheckState.COMPLETE)
                assertThat(progress.marksFor(today)).containsExactly(2)
                assertThat(partials.stored.value).isEmpty()
                cancelAndIgnoreRemainingEvents()
            }
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
